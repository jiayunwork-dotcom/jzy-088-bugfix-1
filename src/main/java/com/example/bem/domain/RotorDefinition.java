package com.example.bem.domain;

import java.util.List;

/**
 * 一次完整 BEM 核算的风轮几何与工况定义。
 *
 * @param tipSpeedRatio 叶尖速比 lambda = omega*R/U，必须为正
 * @param bladeCount    叶片数，必须为正整数
 * @param elements       叶素序列（mu 严格单调递增），至少 3 个站点
 * @param polar          内联翼型极曲线；使用登记翼型时为 null
 * @param polarName      已登记翼型的名字；使用内联极曲线时为 null
 * @param useTipLoss     是否计入普朗特叶尖损失修正
 * @param useHubLoss     是否计入普朗特轮毂损失修正
 * @param hubRadius      轮毂（根切）无量纲半径 mu_root
 */
public record RotorDefinition(double tipSpeedRatio,
                              int bladeCount,
                              List<BladeElement> elements,
                              AirfoilPolar polar,
                              String polarName,
                              boolean useTipLoss,
                              boolean useHubLoss,
                              double hubRadius) {
}
