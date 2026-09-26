package com.example.bem.polar;

import com.example.bem.domain.AirfoilPolar;
import org.springframework.stereotype.Component;

/**
 * 翼型系数表的分段线性插值。
 *
 * 攻角落在表范围之外时按最近端点钳制（翼型表由登记模块保证覆盖整个
 * [-180,180] 度，正常 BEM 迭代不会触到边界；钳制只是最后一道防线）。
 * 本类只负责按攻角取 Cl/Cd，与迭代、叶尖损失无关。
 */
@Component
public class PolarInterpolator {

    /** 一次插值取回的升阻力系数对。 */
    public record Coefficients(double cl, double cd) {
    }

    public Coefficients interpolate(AirfoilPolar polar, double alphaDeg) {
        double[] xs = toArray(polar.alphas());
        double alpha = clamp(alphaDeg, xs[0], xs[xs.length - 1]);

        // 二分查找定位区间
        int lo = 0;
        int hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= alpha) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        if (lo == xs.length - 1) {
            return new Coefficients(polar.cl().get(lo), polar.cd().get(lo));
        }
        double span = xs[hi] - xs[lo];
        double t = span <= 0.0 ? 0.0 : (alpha - xs[lo]) / span;
        double cl = polar.cl().get(lo) + t * (polar.cl().get(hi) - polar.cl().get(lo));
        double cd = polar.cd().get(lo) + t * (polar.cd().get(hi) - polar.cd().get(lo));
        return new Coefficients(cl, cd);
    }

    private static double[] toArray(java.util.List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
