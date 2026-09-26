package com.example.bem.tiploss;

import org.springframework.stereotype.Component;

/**
 * 普朗特轮毂（根部）损失修正，与叶尖损失同形式：
 *
 *   F_hub = (2/pi) * arccos(exp(-f_hub))
 *   f_hub = (B/2) * (mu - mu_root) / (mu * sin(phi))
 *
 * 综合损失因子 F = F_tip * F_hub。根部以外 F_hub 很快趋于 1。
 */
@Component
public class PrandtlHubLoss {

    private static final double F_FLOOR = 1e-4;

    /**
     * @param phiRad     入流角（弧度）
     * @param mu         径向位置 r/R
     * @param muRoot     轮毂（根部切口）无量纲半径
     * @param blades     叶片数
     * @param enabled    是否启用轮毂损失修正
     */
    public double factor(double phiRad, double mu, double muRoot, int blades, boolean enabled) {
        if (!enabled || mu <= muRoot) {
            return enabled ? F_FLOOR : 1.0;
        }
        double sinPhi = Math.sin(phiRad);
        if (sinPhi <= 1e-9) {
            return F_FLOOR;
        }
        double f = 0.5 * blades * (mu - muRoot) / (mu * sinPhi);
        f = Math.min(f, 50.0);
        double factor = (2.0 / Math.PI) * Math.acos(Math.exp(-f));
        return Math.max(factor, F_FLOOR);
    }
}
