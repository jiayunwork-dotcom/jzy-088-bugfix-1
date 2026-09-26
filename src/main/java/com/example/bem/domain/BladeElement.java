package com.example.bem.domain;

/**
 * 单个叶素（叶片沿半径切出的站点）。
 * 径向位置与弦长均按风轮半径 R 做无量纲化：mu = r/R，chord = c/R。
 *
 * @param mu     径向位置 r/R，0 &lt; mu &le; 1
 * @param chord  当地弦长 c/R，必须为正
 * @param twist  当地扭角（度），相对旋转平面的安装角，随半径增大而减小
 */
public record BladeElement(double mu, double chord, double twist) {
}
