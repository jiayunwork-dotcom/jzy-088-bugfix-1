package com.example.bem;

import com.example.bem.domain.RotorSolution;
import com.example.bem.domain.StationSolution;
import com.example.bem.inflow.InflowGeometry;
import com.example.bem.integration.RotorIntegrator;
import com.example.bem.induction.InductionSolver;
import com.example.bem.polar.PolarInterpolator;
import com.example.bem.tiploss.PrandtlHubLoss;
import com.example.bem.tiploss.PrandtlTipLoss;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 气动规律锁定测试（均带容差）：
 *  1. 收敛后叶中区轴向诱导因子落在 [0, 1/2]
 *  2. Cp 为正且不超过贝兹极限 16/27
 *  3. 叶尖速比从偏低抬向设计点，Cp 先上升、再趋于平台，不单调乱跳
 *  4. 关掉叶尖损失后梢部微元推力升高
 *  5. 只减少叶片数，Cp 明显下降
 *  6. 低叶尖速比段（1.5≤λ≤10）Cp 恒正、逐站微元扭矩恒正
 *  7. λ=5/6/8/10 的 Cp、Ct 参考值小数点后四位锁定
 */
class AerodynamicLawsTest {

    private RotorIntegrator integrator;

    @BeforeEach
    void setUp() {
        InductionSolver solver = new InductionSolver(
                new InflowGeometry(), new PolarInterpolator(),
                new PrandtlTipLoss(), new PrandtlHubLoss());
        integrator = new RotorIntegrator(solver);
    }

    private RotorSolution run(double tsr, int blades, boolean tipLoss, boolean hubLoss) {
        return integrator.integrate(TestFixtures.sampleRotor(), TestFixtures.polar(),
                tsr, blades, tipLoss, hubLoss, TestFixtures.HUB);
    }

    @Test
    @DisplayName("叶中区(0.3≤μ≤0.8)轴向诱导因子收敛后落在 [0, 0.5]")
    void midSpanAxialInductionInPhysicalRange() {
        RotorSolution sol = run(6.0, 3, true, true);
        for (StationSolution s : sol.stations()) {
            if (s.mu() >= 0.30 && s.mu() <= 0.80) {
                assertTrue(s.axialInduction() >= 0.0,
                        "μ=" + s.mu() + " a 不应为负：" + s.axialInduction());
                assertTrue(s.axialInduction() <= 0.5,
                        "μ=" + s.mu() + " a 不应超过 0.5：" + s.axialInduction());
                assertTrue(s.axialInduction() > 0.15,
                        "设计风轮叶中 a 应明显非零：" + s.axialInduction());
            }
        }
    }

    @Test
    @DisplayName("Cp 为正且严格不超过贝兹极限 16/27")
    void powerCoefficientPositiveAndBelowBetz() {
        for (double tsr : new double[]{4.0, 5.0, 6.0, 7.0}) {
            RotorSolution sol = run(tsr, 3, true, true);
            assertTrue(sol.powerCoefficient() > 0.0,
                    "λ=" + tsr + " Cp 应为正：" + sol.powerCoefficient());
            assertTrue(sol.powerCoefficient() < RotorSolution.BETZ_LIMIT,
                    "λ=" + tsr + " Cp 不得超过贝兹极限：" + sol.powerCoefficient());
            assertEquals(0.592592, RotorSolution.BETZ_LIMIT, 1e-6);
        }
    }

    @Test
    @DisplayName("内置三叶片算例 Cp 为正且低于贝兹极限，量级合理（可手工核对）")
    void builtInSampleIsSane() {
        RotorSolution sol = run(6.0, 3, true, true);
        assertTrue(sol.powerCoefficient() > 0.35,
                "设计点 Cp 应在合理高值：" + sol.powerCoefficient());
        assertTrue(sol.powerCoefficient() < RotorSolution.BETZ_LIMIT);
        assertTrue(sol.thrustCoefficient() > 0.5 && sol.thrustCoefficient() < 1.0);
        // Cp = λ·Cq 自洽
        assertEquals(sol.powerCoefficient(),
                6.0 * sol.torqueCoefficient(), 1e-9);
    }

    @Test
    @DisplayName("叶尖速比扫描：Cp 先显著上升、随后在设计点附近趋于平台（覆盖低叶尖速比段）")
    void cpRisesThenPlateausOverTsR() {
        // 扫描区间从 1.5 开始，不再绕开低叶尖速比段
        double[] tsr = {1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        double[] cp = new double[tsr.length];
        for (int i = 0; i < tsr.length; i++) {
            cp[i] = run(tsr[i], 3, true, true).powerCoefficient();
        }
        // 上升段：1.5→6 逐步上行，不允许先跌成负值再穿过零点爬回来
        int design = 5; // λ=6 的下标
        for (int i = 1; i <= design; i++) {
            assertTrue(cp[i] > cp[i - 1],
                    "上升段不得倒退：λ=" + tsr[i - 1] + " Cp=" + cp[i - 1]
                            + "，λ=" + tsr[i] + " Cp=" + cp[i]);
        }
        double rise = cp[design] - cp[0];
        assertTrue(rise > 0.10,
                "Cp 从 λ=1.5 到 6 应显著上升，实际 ΔCp=" + rise);

        // 平台段：6、7、8 三点围绕峰值波动，相邻变化远小于上升幅度，
        // 且不出现单调陡降（取相邻差绝对值之和）
        double peak = Math.max(cp[design], Math.max(cp[design + 1], cp[design + 2]));
        for (int i = design; i < cp.length; i++) {
            assertTrue(peak - cp[i] < 0.10,
                    "平台段 Cp 偏离峰值过大：" + cp[i] + "（峰 " + peak + "）");
            assertTrue(cp[i] > cp[0] + 0.05,
                    "平台段不得跌回上升起点附近");
        }
    }

    @Test
    @DisplayName("关掉叶尖损失后，梢部微元推力 dCt 高于计入损失时")
    void disablingTipLossRaisesTipElementThrust() {
        RotorSolution withLoss = run(6.0, 3, true, false);
        RotorSolution withoutLoss = run(6.0, 3, false, false);
        StationSolution tipOn = last(withLoss.stations());
        StationSolution tipOff = last(withoutLoss.stations());
        assertEquals(0.95, tipOn.mu(), 1e-9);
        assertTrue(tipOff.dCt() > tipOn.dCt(),
                "无叶尖损失梢部 dCt=" + tipOff.dCt()
                        + " 应高于有损失 " + tipOn.dCt());
        // 差距应是可辨的（至少 10%）
        assertTrue(tipOff.dCt() > 1.10 * tipOn.dCt(),
                "梢部载荷差异应可辨识");
        // 有损失时叶尖 F 明显小于 1，无损失恒为 1
        assertTrue(tipOn.prandtlFactor() < 0.9);
        assertEquals(1.0, tipOff.prandtlFactor(), 1e-12);
    }

    @Test
    @DisplayName("其它不变、叶片数 3→2，功率系数明显下降")
    void fewerBladesReducesPowerCoefficient() {
        RotorSolution three = run(6.0, 3, true, true);
        RotorSolution two = run(6.0, 2, true, true);
        assertTrue(three.powerCoefficient() - two.powerCoefficient() > 0.03,
                "B=2 的 Cp=" + two.powerCoefficient()
                        + " 应明显低于 B=3 的 " + three.powerCoefficient());
    }

    @Test
    @DisplayName("低叶尖速比段 1.5≤λ≤10：Cp 恒为正且低于贝兹极限")
    void lowTsrPowerPositiveAndBelowBetz() {
        for (double tsr = 1.5; tsr <= 10.0 + 1e-9; tsr += 0.5) {
            RotorSolution sol = run(tsr, 3, true, true);
            assertTrue(sol.powerCoefficient() > 0.0,
                    "λ=" + tsr + " Cp 应为正（不得变成耗功的风扇）："
                            + sol.powerCoefficient());
            assertTrue(sol.powerCoefficient() < RotorSolution.BETZ_LIMIT,
                    "λ=" + tsr + " Cp 不得超过贝兹极限：" + sol.powerCoefficient());
            assertTrue(sol.torqueCoefficient() > 0.0,
                    "λ=" + tsr + " Cq 应为正：" + sol.torqueCoefficient());
        }
    }

    @Test
    @DisplayName("λ=2 与 λ=3 时每个站点的微元扭矩都为正")
    void lowTsrElementTorquePositiveAtEveryStation() {
        for (double tsr : new double[]{2.0, 3.0}) {
            RotorSolution sol = run(tsr, 3, true, true);
            for (StationSolution s : sol.stations()) {
                assertTrue(s.dCq() > 0.0,
                        "λ=" + tsr + "、μ=" + s.mu() + " 微元扭矩应为正：" + s.dCq());
            }
        }
    }

    @Test
    @DisplayName("参考值锁定：λ=5/6/8/10 的 Cp、Ct 小数点后四位保持原样")
    void referenceCoefficientsLocked() {
        // {λ, Cp, Ct}，与设计点核算结果一致到小数点后四位
        double[][] expected = {
                {5.0, 0.4059, 0.6528},
                {6.0, 0.4170, 0.7189},
                {8.0, 0.3766, 0.7924},
                {10.0, 0.2708, 0.8272},
        };
        for (double[] row : expected) {
            RotorSolution sol = run(row[0], 3, true, true);
            assertEquals(row[1], sol.powerCoefficient(), 1e-4,
                    "λ=" + row[0] + " Cp 偏离参考值");
            assertEquals(row[2], sol.thrustCoefficient(), 1e-4,
                    "λ=" + row[0] + " Ct 偏离参考值");
        }
    }

    @Test
    @DisplayName("各站均在限定轮数内收敛，解为有限值")
    void allStationsConvergeToFiniteValues() {
        RotorSolution sol = run(6.0, 3, true, true);
        for (StationSolution s : sol.stations()) {
            assertTrue(s.iterations() > 0 && s.iterations() <= 400);
            assertTrue(Double.isFinite(s.axialInduction())
                    && Double.isFinite(s.tangentialInduction())
                    && Double.isFinite(s.dCt()) && Double.isFinite(s.dCq()));
            assertFalse(Double.isNaN(s.angleOfAttack()));
        }
    }

    private static StationSolution last(List<StationSolution> stations) {
        return stations.get(stations.size() - 1);
    }
}
