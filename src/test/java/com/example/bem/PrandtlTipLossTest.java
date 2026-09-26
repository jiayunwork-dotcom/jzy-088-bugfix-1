package com.example.bem;

import com.example.bem.tiploss.PrandtlTipLoss;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 普朗特叶尖损失因子单测。 */
class PrandtlTipLossTest {

    private final PrandtlTipLoss tipLoss = new PrandtlTipLoss();

    @Test
    @DisplayName("关闭修正时恒为 1")
    void disabledIsOne() {
        assertEquals(1.0, tipLoss.factor(0.2, 0.95, 3, false), 1e-12);
    }

    @Test
    @DisplayName("叶尖 μ=1 处 F 趋于 0；远离叶尖趋于 1")
    void tipZeroRootOne() {
        double phi = Math.toRadians(10);
        assertTrue(tipLoss.factor(phi, 1.0, 3, true) < 0.05);
        assertTrue(tipLoss.factor(phi, 0.3, 3, true) > 0.99);
    }

    @Test
    @DisplayName("因子落在 (0,1]")
    void bounded() {
        for (double mu = 0.25; mu <= 1.0; mu += 0.05) {
            double f = tipLoss.factor(Math.toRadians(8), mu, 3, true);
            assertTrue(f > 0.0 && f <= 1.0, "mu=" + mu + " F=" + f);
        }
    }
}
