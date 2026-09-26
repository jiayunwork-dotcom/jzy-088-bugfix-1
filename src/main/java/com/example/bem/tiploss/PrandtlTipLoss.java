package com.example.bem.tiploss;

import org.springframework.stereotype.Component;

/**
 * 普朗特叶尖损失修正。
 *
 *   F_tip = (2/pi) * arccos(exp(-f))
 *   f = (B/2) * (1 - mu) / (mu * sin(phi))
 *
 * 其中 B 为叶片数，mu = r/R，phi 为入流角。
 * 当调用方关闭修正时直接返回 1（动量关系退化为无损失形式）。
 * 这里只算叶尖损失；本服务不建模轮毂损失。
 */
@Component
public class PrandtlTipLoss {

    /** F 的数值下限，避免 exp 的自变量在极端几何下溢出。 */
    private static final double F_FLOOR = 1e-4;

    /**
     * @param phiRad   入流角（弧度）
     * @param mu       径向位置 r/R
     * @param blades   叶片数
     * @param enabled  是否启用叶尖损失修正
     */
    public double factor(double phiRad, double mu, int blades, boolean enabled) {
        if (!enabled) {
            return 1.0;
        }
        double sinPhi = Math.sin(phiRad);
        if (mu <= 0.0 || sinPhi <= 1e-9) {
            return F_FLOOR;
        }
        double f = 0.5 * blades * (1.0 - mu) / (mu * sinPhi);
        // f 很大时 exp(-f)=0、F=1；f 很小时 F 趋近 0
        f = Math.min(f, 50.0);
        double factor = (2.0 / Math.PI) * Math.acos(Math.exp(-f));
        return Math.max(factor, F_FLOOR);
    }
}
