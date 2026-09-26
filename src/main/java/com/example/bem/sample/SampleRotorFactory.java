package com.example.bem.sample;

import com.example.bem.domain.BladeElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置三叶片风轮算例，几何可手工核对。
 *
 * 设计叶尖速比 λ_d = 6，采用贝兹最优诱导（a=1/3）下的经典简化设计：
 *   入流角  φ(r) = arctan(2/(3 λ_d μ))
 *   扭角    θ(r) = φ(r) - α_d，设计攻角 α_d = 6°（示例翼型 Cl≈0.83、低阻）
 *   弦长    c(r)/R = 4π μ sin²φ / (B C_Ld cosφ)
 *           （由动量—叶素平衡 4a(1−a)=σ Cn(1−a)²/sin²φ 取 a=1/3 反解）
 * 径向取 9 个站点，μ 从 0.20 到 0.95，单调排列。
 */
@Component
public class SampleRotorFactory {

    public static final int DESIGN_BLADES = 3;
    public static final double DESIGN_TSR = 6.0;
    public static final double DESIGN_ALPHA_DEG = 6.0;
    public static final double DESIGN_CL = 0.83;
    public static final double HUB_RADIUS = 0.20;

    private static final double[] STATIONS = {
            0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 0.95
    };

    public List<BladeElement> build() {
        List<BladeElement> elements = new ArrayList<>(STATIONS.length);
        for (double mu : STATIONS) {
            double phiRad = Math.atan(2.0 / (3.0 * DESIGN_TSR * mu));
            double twistDeg = Math.toDegrees(phiRad) - DESIGN_ALPHA_DEG;
            double chord = 4.0 * Math.PI * mu * Math.sin(phiRad) * Math.sin(phiRad)
                    / (DESIGN_BLADES * DESIGN_CL * Math.cos(phiRad));
            elements.add(new BladeElement(mu, chord, twistDeg));
        }
        return elements;
    }
}
