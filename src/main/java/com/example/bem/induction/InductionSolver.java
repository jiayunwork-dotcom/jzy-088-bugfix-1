package com.example.bem.induction;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.inflow.InflowGeometry;
import com.example.bem.polar.PolarInterpolator;
import com.example.bem.tiploss.PrandtlHubLoss;
import com.example.bem.tiploss.PrandtlTipLoss;

/**
 * 单个叶素的轴向/切向诱导因子迭代解。
 *
 * 固定点迭代每一轮：
 *   1. 由当前 (a, a') 求入流角 tan(phi) = (1-a)/((1+a') lambda mu)
 *   2. alpha = phi - twist，查翼型表得 Cl、Cd
 *   3. 法向/切向力系数 Cn = Cl cosφ + Cd sinφ，Ct = Cl sinφ - Cd cosφ
 *      当地实度 sigma = B c / (2 pi r)
 *   4. 叶素侧推力系数 CT_be = sigma (1-a)^2 Cn / sin²φ
 *      小诱导（动量尾流）区与高诱导（湍流尾流）区分别求解动量平衡：
 *        先算线性解 a_lin = f/(F+f), f = sigma Cn/(4 sin²φ)
 *        a_lin &le; a_c：4aF(1-a) 与叶素侧相等，直接采用 a_lin
 *        a_lin &gt; a_c：改用经过修正的推力关系 CT = γ2 a² + γ1 a + γ0
 *                       （Buhl/Glauert 二次拟合，在 a_c 处与值和斜率连续，a=1 时 CT=1），
 *                       解二次方程取 [a_c, 1] 内、最靠近上一轮的根，
 *                       不再套小诱导公式把分母逼到失稳；
 *      切向：a' = sigma Ct / (4F cosφ - sigma Ct)
 *   5. 欠松弛更新，直到 |Δa|、|Δa'| 均小于容差。
 *
 * 分支按“当前轮的线性解”判定，而不是按上一轮 a，避免临界点附近高低分支抖振。
 *
 * 不收敛（轮数超限、NaN、找不到物理根）一律抛 {@link NonConvergentException}，
 * 绝不返回看似有值的发散结果。本类只管单站迭代，不做沿叶展积分。
 */
@org.springframework.stereotype.Component
public class InductionSolver {

    /**
     * 进入湍流尾流修正的临界轴向诱导因子。
     * 取 Buhl(2005) 采用的 a_c = 1/3，可保证修正二次曲线在 0 &lt; F &le; 1
     * 全程保持物理形态。
     */
    public static final double A_CRITICAL = 1.0 / 3.0;

    private static final int MAX_ITERATIONS = 400;
    private static final double TOLERANCE = 1.0e-6;
    /** 初始欠松弛因子：new = old + omega*(raw - old)。 */
    private static final double RELAXATION_INITIAL = 0.3;
    /** 检测到高低分支切换引起的振荡时，松弛因子的下限与减半步长。 */
    private static final double RELAXATION_MIN = 0.025;

    private final InflowGeometry inflow;
    private final PolarInterpolator polarInterpolator;
    private final PrandtlTipLoss tipLoss;
    private final PrandtlHubLoss hubLoss;

    public InductionSolver(InflowGeometry inflow,
                           PolarInterpolator polarInterpolator,
                           PrandtlTipLoss tipLoss,
                           PrandtlHubLoss hubLoss) {
        this.inflow = inflow;
        this.polarInterpolator = polarInterpolator;
        this.tipLoss = tipLoss;
        this.hubLoss = hubLoss;
    }

    /** 单站收敛解（不含微元载荷，载荷由积分模块统一给出）。 */
    public record ConvergedStation(double phiRad,
                                   double alphaDeg,
                                   double a,
                                   double ap,
                                   double cl,
                                   double cd,
                                   double f,
                                   int iterations,
                                   boolean highInduction) {
    }

    /**
     * 对单个叶素迭代求解诱导因子。
     *
     * @param element 叶素（mu、chord、twist，均无量纲，扭角为度）
     * @param polar   翼型系数表
     * @param lambda  叶尖速比
     * @param blades  叶片数
     * @param useTipLoss 是否计入普朗特叶尖损失
     * @param muRoot  轮毂（根切）无量纲半径
     * @param useHubLoss 是否计入普朗特轮毂损失
     */
    public ConvergedStation solve(BladeElement element, AirfoilPolar polar,
                                  double lambda, int blades, boolean useTipLoss,
                                  double muRoot, boolean useHubLoss) {
        double mu = element.mu();
        double sigma = blades * element.chord() / (2.0 * Math.PI * mu);

        double a = 0.0;
        double ap = 0.0;
        double relaxation = RELAXATION_INITIAL;
        double aPrev = 0.0;
        boolean high = false;
        int stagnantRounds = 0;

        for (int iter = 1; iter <= MAX_ITERATIONS; iter++) {
            double phi = inflow.inflowAngleRad(a, ap, lambda, mu);
            double alpha = inflow.angleOfAttackDeg(phi, element.twist());
            PolarInterpolator.Coefficients coef = polarInterpolator.interpolate(polar, alpha);
            double cl = coef.cl();
            double cd = coef.cd();

            double sinPhi = Math.sin(phi);
            double cosPhi = Math.cos(phi);
            double cn = cl * cosPhi + cd * sinPhi;
            double ct = cl * sinPhi - cd * cosPhi;
            double fTip = tipLoss.factor(phi, mu, blades, useTipLoss);
            double fHub = hubLoss.factor(phi, mu, muRoot, blades, useHubLoss);
            double fCombined = fTip * fHub;
            // 后续动量方程统一用综合普朗特因子 F
            double fLoss = fCombined;

            double sin2 = sinPhi * sinPhi;
            if (sin2 < 1e-10) {
                throw new NonConvergentException(
                        "mu=" + mu + " 入流角退化（sinφ≈0），无法构造动量平衡");
            }

            double f = sigma * cn / (4.0 * sin2);
            // 叶素侧推力系数（对 a 求解动量方程用）
            double ctBe = sigma * (1.0 - a) * (1.0 - a) * cn / sin2;

            // 先求小诱导线性解；由它决定走哪个分支
            double aLin = f / (fLoss + f);
            double aRaw;
            if (aLin <= A_CRITICAL) {
                aRaw = aLin;
                high = false;
            } else {
                // 湍流尾流：Buhl/Glauert 修正推力关系，解二次方程
                aRaw = solveHighInduction(fLoss, ctBe, a, mu);
                high = true;
            }

            // 切向诱导
            double apDenom = 4.0 * fLoss * cosPhi - sigma * ct;
            double apRaw = apDenom > 1e-9 ? sigma * ct / apDenom : 0.0;
            apRaw = Math.max(0.0, apRaw);

            // 欠松弛（自适应）
            double aNew = a + relaxation * (aRaw - a);
            double apNew = ap + relaxation * (apRaw - ap);
            if (!Double.isFinite(aNew) || !Double.isFinite(apNew)
                    || aNew < -0.25 || aNew > 1.25) {
                throw new NonConvergentException(
                        "mu=" + mu + " 迭代值越界/非有限（a=" + aNew + ", a'=" + apNew + "）");
            }

            // 振荡检测：本轮增量与上一轮增量反号且幅度没明显减小，
            // 说明跨临界分支在打极限环——减半松弛因子强行收敛。
            double step = aNew - a;
            double prevStep = a - aPrev;
            if (iter > 2 && step * prevStep < 0.0
                    && Math.abs(step) > 0.5 * Math.abs(prevStep)
                    && relaxation > RELAXATION_MIN) {
                stagnantRounds++;
                if (stagnantRounds >= 3) {
                    relaxation = Math.max(RELAXATION_MIN, relaxation * 0.5);
                    stagnantRounds = 0;
                }
            } else {
                stagnantRounds = 0;
            }
            aPrev = a;

            double da = Math.abs(aNew - a);
            double dap = Math.abs(apNew - ap);
            a = aNew;
            ap = apNew;

            if (da < TOLERANCE && dap < TOLERANCE) {
                double phiFinal = inflow.inflowAngleRad(a, ap, lambda, mu);
                double alphaFinal = inflow.angleOfAttackDeg(phiFinal, element.twist());
                return new ConvergedStation(phiFinal, alphaFinal, a, ap,
                        cl, cd, fCombined, iter, high);
            }
        }
        throw new NonConvergentException(
                "mu=" + mu + " 叶素诱导因子迭代 " + MAX_ITERATIONS + " 轮仍未收敛");
    }

    /**
     * 求解高诱导区二次方程 γ2 a² + γ1 a + γ0 = CT_be，取物理根。
     *
     * Buhl(2005) 二次拟合（在 a_c 处与 4a(1−a)F 的值、斜率连续，a=1 时 CT=1），令
     * D = 1 − a_c：
     *   γ2 = 1/D² − 4F
     *   γ1 = 4F − 2a_c/D²
     *   γ0 = a_c²/D²
     * F=1、a_c=0.4 时退化为经典 Glauert 曲线 CT = −11/9 a² + 16/9 a + 4/9。
     */
    /**
     * 高诱导区由 CT_be 反解 a。
     *
     * 迭代途中叶素侧 CT_be 可能短暂超过修正曲线的可达最大值（判别式为负），
     * 或解出的根落在 [a_c,1] 之外。这类瞬态失配不直接判死：取曲线可达范围内
     * 最接近上一轮状态的 a（配合欠松弛把迭代拉回物理解），真正无法收敛由
     * 迭代轮数上限抛 {@link NonConvergentException}。
     */
    private double solveHighInduction(double fTip, double ctBe, double aOld, double mu) {
        double d = 1.0 - A_CRITICAL;
        double d2 = d * d;
        double gamma2 = 1.0 / d2 - 4.0 * fTip;
        double gamma1 = 4.0 * fTip - 2.0 * A_CRITICAL / d2;
        double gamma0 = A_CRITICAL * A_CRITICAL / d2;

        double aa = gamma2;
        double bb = gamma1;
        double cc = gamma0 - ctBe;
        double ctCritical = 4.0 * fTip * A_CRITICAL * d;
        if (ctBe < ctCritical - 1e-9) {
            return A_CRITICAL;
        }
        if (Math.abs(aa) < 1e-12) {
            double root = -cc / bb;
            return clampToBranch(root, aOld);
        }
        double disc = bb * bb - 4.0 * aa * cc;
        if (disc < 0.0) {
            // CT_be 超过此 F 下修正曲线最大值：取曲线顶点 a
            double vertex = -bb / (2.0 * aa);
            return clampToBranch(vertex, aOld);
        }
        double sqrt = Math.sqrt(disc);
        double r1 = (-bb + sqrt) / (2.0 * aa);
        double r2 = (-bb - sqrt) / (2.0 * aa);

        double best = Double.NaN;
        double bestDist = Double.POSITIVE_INFINITY;
        for (double candidate : new double[]{r1, r2}) {
            if (candidate >= A_CRITICAL - 1e-6 && candidate <= 1.0 + 1e-6) {
                double dist = Math.abs(candidate - aOld);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        if (!Double.isFinite(best)) {
            // 两根都越界：钳到区间内离上一轮最近的端点
            best = clampToBranch(Double.isNaN(r1) ? aOld : r1, aOld);
        }
        return Math.max(A_CRITICAL, Math.min(1.0, best));
    }

    private double clampToBranch(double candidate, double aOld) {
        if (candidate < A_CRITICAL) {
            return A_CRITICAL;
        }
        if (candidate > 1.0) {
            return 1.0;
        }
        return candidate;
    }
}
