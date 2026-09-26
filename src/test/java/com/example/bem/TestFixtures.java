package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.sample.SampleAirfoilFactory;
import com.example.bem.sample.SampleRotorFactory;

import java.util.List;

/**
 * 测试夹具：复用内置示例翼型与风轮几何，并提供少量定制几何构造。
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static AirfoilPolar polar() {
        return new SampleAirfoilFactory().build();
    }

    public static List<BladeElement> sampleRotor() {
        return new SampleRotorFactory().build();
    }

    public static final double HUB = SampleRotorFactory.HUB_RADIUS;

    /** 用均匀弦长、线性扭角造一副几何（供非法/不收敛场景）。 */
    public static List<BladeElement> uniformRotor(double chord, double twistRoot,
                                                  double twistTip, int n) {
        double mu0 = 0.25;
        double mu1 = 0.95;
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> {
                    double t = (double) (i - 1) / (n - 1);
                    double mu = mu0 + t * (mu1 - mu0);
                    double twist = twistRoot + t * (twistTip - twistRoot);
                    return new BladeElement(mu, chord, twist);
                })
                .toList();
    }
}
