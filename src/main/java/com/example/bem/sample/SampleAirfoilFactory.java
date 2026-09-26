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

    /**
     * 平板阻力峰值（α=±90°，来流正对板面）。实测平板约 1.9~2.0。
     * 深失速后翼型受力近似为垂直于弦面的单个法向力
     * CN = CD_MAX·sinα，升/阻系数是它在升力、阻力方向上的投影：
     *   Cl = CN·cosα = CD_MAX·sinα·cosα
     *   Cd = CN·sinα = CD_MAX·sin²α
     * 两个系数必须投影自同一个 CN；若各自独立给值（例如 Cd 带常数偏移），
     * 大攻角下升阻比会被压到远低于真实平板，Ct=Cl·sinφ−Cd·cosφ 在大入流角
     * 工况（低叶尖速比）下会假性变负。
     */
    private static final double CD_MAX = 2.0;

    /**
     * 失速过渡完成的攻角宽度（度）。真实翼型在失速后约 2°~3° 内
     * 升力骤降、阻力骤升，随后受力退化为平板法向力；过渡取 3°。
     * 过渡段形状只有落进表格点才有效，故 15°~25° 区间每 1° 取一点。
     */
    private static final double STALL_TRANSITION_DEG = 3.0;

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

        // 失速后深失速/平板延拓：15°~25° 每 1° 一点（解析失速过渡形状），
        // 其余每 5° 一点，整周到 ±180°
        for (int alpha = -180; alpha <= 180; alpha++) {
            double abs = Math.abs(alpha);
            if (abs < 15) {
                continue; // 附着区由显式表覆盖
            }
            if (abs > 25 && alpha % 5 != 0) {
                continue; // 深失速区平板曲线变化平缓，5° 一点足够
            }
            double rad = Math.toRadians(alpha);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);

            // 平板/Viterna：CN = CD_MAX·sinα 的投影，Cd 最大约 2.0（垂直来流）
            double clPlate = CD_MAX * sin * cos;
            double cdPlate = CD_MAX * sin * sin;

            double clFlat;
            double cdFlat;
            if (abs <= 15.0 + STALL_TRANSITION_DEG) {
                // 失速过渡：从附着数据快速过渡到平板曲线
                double t = (abs - 15.0) / STALL_TRANSITION_DEG;
                double sign = alpha < 0 ? -1.0 : 1.0;
                double cdAttached = alpha < 0 ? 0.060 : 0.078;
                double clAttached = sign * 1.16;
                clFlat = clAttached + t * (clPlate - clAttached);
                cdFlat = cdAttached + t * (cdPlate - cdAttached);
            } else {
                clFlat = clPlate;
                cdFlat = cdPlate;
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
