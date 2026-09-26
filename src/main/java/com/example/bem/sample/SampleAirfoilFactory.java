package com.example.bem.sample;

import com.example.bem.domain.AirfoilPolar;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置示例翼型（名义为 NACA 4412 风格的中等厚度翼型）极曲线构造器。
 *
 * 附着区（|α|≤15°）给出显式升阻力数据；失速后（15°&lt;|α|≤180°）用
 * 平板/Viterna 风格经验式延拓，保证整周攻角都有值、插值不需要外推。
 * 角度单位为度。
 */
@Component
public class SampleAirfoilFactory {

    public static final String SAMPLE_POLAR_NAME = "NACA4412-SAMPLE";

    public AirfoilPolar build() {
        List<Double> a = new ArrayList<>();
        List<Double> cl = new ArrayList<>();
        List<Double> cd = new ArrayList<>();

        // 附着区显式数据（零度附近升力线斜率约 2π/180，零升攻角约 -2°）
        double[][] attached = {
                {-15, -1.02, 0.060},
                {-10, -0.70, 0.020},
                {-5,  -0.30, 0.011},
                {-2,   0.00, 0.010},
                {0,    0.22, 0.010},
                {3,    0.52, 0.010},
                {6,    0.83, 0.012},
                {9,    1.10, 0.019},
                {12,   1.24, 0.035},
                {15,   1.16, 0.078},
        };

        // 失速后深失速/平板延拓（每 5° 一点，整周到 ±180°）
        for (int alpha = -180; alpha <= 180; alpha += 5) {
            if (alpha > -15 && alpha < 15) {
                continue; // 附着区由显式表覆盖
            }
            double rad = Math.toRadians(alpha);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            double abs = Math.abs(alpha);

            double clFlat;
            double cdFlat;
            if (abs <= 20) {
                // 15°~20° 之间从附着数据平滑过渡到平板
                double t = (abs - 15.0) / 5.0;
                double cdPlate = 0.7 + 0.3 * Math.abs(cos);
                double clPlate = 0.9 * sin * Math.cos(rad);
                double sign = alpha < 0 ? -1.0 : 1.0;
                double cdAttached = alpha < 0 ? 0.060 : 0.078;
                double clAttached = sign * 1.16;
                clFlat = clAttached + t * (clPlate - clAttached);
                cdFlat = cdAttached + t * (cdPlate - cdAttached);
            } else {
                // 平板/Viterna：Cd 最大约 2.0（垂直），最小约 0.7（平行）
                clFlat = 0.9 * sin * Math.cos(rad);
                cdFlat = 0.7 + 1.3 * sin * sin;
            }
            a.add((double) alpha);
            cl.add(clFlat);
            cd.add(cdFlat);
        }

        for (double[] row : attached) {
            a.add(row[0]);
            cl.add(row[1]);
            cd.add(row[2]);
        }

        // 按攻角排序（延拓点与附着点合并）
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            order.add(i);
        }
        order.sort((i, j) -> Double.compare(a.get(i), a.get(j)));
        List<Double> sa = new ArrayList<>();
        List<Double> scl = new ArrayList<>();
        List<Double> scd = new ArrayList<>();
        for (int idx : order) {
            // 跳过重复攻角，保留先出现的
            if (!sa.isEmpty() && Math.abs(sa.get(sa.size() - 1) - a.get(idx)) < 1e-9) {
                continue;
            }
            sa.add(a.get(idx));
            scl.add(cl.get(idx));
            scd.add(cd.get(idx));
        }
        return new AirfoilPolar(SAMPLE_POLAR_NAME, sa, scl, scd);
    }
}
