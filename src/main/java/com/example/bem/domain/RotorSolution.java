package com.example.bem.domain;

import java.util.List;

/**
 * 整片风轮沿半径积分的核算结果。
 *
 * @param powerCoefficient  功率系数 Cp，不得超过贝兹极限 16/27
 * @param thrustCoefficient 推力系数 Ct
 * @param torqueCoefficient 扭矩系数 Cq（Cp = lambda * Cq）
 * @param stations          每个径向站点的局部解
 */
public record RotorSolution(double powerCoefficient,
                            double thrustCoefficient,
                            double torqueCoefficient,
                            List<StationSolution> stations) {

    /** 贝兹极限：Cp_max = 16/27。 */
    public static final double BETZ_LIMIT = 16.0 / 27.0;
}
