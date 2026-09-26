package com.example.bem.inflow;

import org.springframework.stereotype.Component;

/**
 * 入流几何：由诱导因子计算入流角 phi 与当地攻角 alpha。
 *
 * 入流速度三角形中（速度以远前方风速 U 无量纲化）：
 *   轴向速度 = 1 - a
 *   切向速度 = (1 + a') * lambda * mu
 * 所以
 *   tan(phi) = (1 - a) / ((1 + a') * lambda * mu)
 *   alpha    = phi - twist
 *
 * 本类只做这两个纯几何关系，不碰迭代、翼型或积分。
 */
@Component
public class InflowGeometry {

    /** 切向速度为零的退化情形（理论上正 lambda、正 mu 不会发生）下的入流角上限。 */
    private static final double PHI_MAX_RAD = Math.PI / 2 - 1e-9;

    /**
     * 计算入流角。
     *
     * @param a       轴向诱导因子
     * @param ap      切向诱导因子
     * @param lambda  叶尖速比
     * @param mu      径向位置 r/R
     * @return 入流角（弧度），范围 [0, pi/2)
     */
    public double inflowAngleRad(double a, double ap, double lambda, double mu) {
        double axial = 1.0 - a;
        double tangential = (1.0 + ap) * lambda * mu;
        if (tangential <= 1e-12) {
            return PHI_MAX_RAD;
        }
        double phi = Math.atan2(axial, tangential);
        if (phi < 0.0) {
            phi = 0.0;
        } else if (phi > PHI_MAX_RAD) {
            phi = PHI_MAX_RAD;
        }
        return phi;
    }

    /** 入流角，单位度。 */
    public double inflowAngleDeg(double a, double ap, double lambda, double mu) {
        return Math.toDegrees(inflowAngleRad(a, ap, lambda, mu));
    }

    /**
     * 当地攻角 alpha = phi - twist。
     *
     * @param inflowRad 入流角（弧度）
     * @param twistDeg  扭角（度）
     * @return 攻角（度）
     */
    public double angleOfAttackDeg(double inflowRad, double twistDeg) {
        return Math.toDegrees(inflowRad) - twistDeg;
    }
}
