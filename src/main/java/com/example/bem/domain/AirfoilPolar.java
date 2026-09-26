package com.example.bem.domain;

import java.util.List;

/**
 * 翼型升阻力系数表（极曲线），角度单位：度。
 *
 * @param name    登记表中的具名标识，内联极曲线可为 null
 * @param alphas  攻角序列（度），严格单调递增
 * @param cl      与 alphas 一一对应的升力系数
 * @param cd      与 alphas 一一对应的阻力系数
 */
public record AirfoilPolar(String name, List<Double> alphas, List<Double> cl, List<Double> cd) {
}
