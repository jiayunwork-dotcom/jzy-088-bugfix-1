package com.example.bem.domain;

/**
 * 单个叶素的 BEM 收敛结果（角度为度，载荷为无量纲微元系数）。
 *
 * @param mu               径向位置 r/R
 * @param chord            弦长 c/R
 * @param twist            扭角（度）
 * @param inflowAngle      入流角 phi（度）
 * @param angleOfAttack    当地攻角 alpha = phi - twist（度）
 * @param axialInduction   轴向诱导因子 a
 * @param tangentialInduction 切向诱导因子 a'
 * @param cl               升力系数
 * @param cd               阻力系数
 * @param prandtlFactor    普朗特叶尖损失因子 F（关掉修正时恒为 1）
 * @param iterations       迭代收敛轮数
 * @param highInduction    收敛点是否落在湍流尾流（高诱导）修正分支
 * @param dCt              微元推力系数对 r/R 的导数（dCt/dmu，环面定义）
 * @param dCq              微元扭矩系数对 r/R 的导数（dCq/dmu，环面定义）
 */
public record StationSolution(double mu,
                              double chord,
                              double twist,
                              double inflowAngle,
                              double angleOfAttack,
                              double axialInduction,
                              double tangentialInduction,
                              double cl,
                              double cd,
                              double prandtlFactor,
                              int iterations,
                              boolean highInduction,
                              double dCt,
                              double dCq) {
}
