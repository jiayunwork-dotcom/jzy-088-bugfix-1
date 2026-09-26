package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.RotorSolution;
import com.example.bem.domain.StationSolution;
import com.example.bem.inflow.InflowGeometry;
import com.example.bem.integration.RotorIntegrator;
import com.example.bem.induction.InductionSolver;
import com.example.bem.polar.PolarInterpolator;
import com.example.bem.tiploss.PrandtlHubLoss;
import com.example.bem.tiploss.PrandtlTipLoss;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 低叶尖速比段锁定测试。
 *
 * 内置算例按 λ_d=6 设计，低 λ 下入流角增大、各站攻角进入失速后区。
 * 这一段曾经因示例翼型深失速延拓失真（升/阻系数不是同一法向力的投影，
 * 大攻角升阻比被压到远低于真实平板）而出现负微元扭矩、负 Cp。
 * 以下用例把修复后的期望锁死，扫描区间覆盖 1.5~10，不再绕开出问题的区段：
 *  1. λ∈[1.5,10] 任取，Cp 为正且低于贝兹极限
 *  2. 从低 λ 抬向设计点，Cp 整体单调上升，不先跌负再穿零爬回
 *  3. λ=2、λ=3 时每个站点的微元扭矩都为正
 *  4. λ=5/6/8/10 的 Cp、Ct 与既有基准在小数点后四位保持一致
 *  5. 调用方内联传一张（物理上很差的）翼型表时照实计算，负 Cp 原样返回，
 *     不在出结果前压零或取绝对值
 */
class LowTipSpeedRatioTest {

    private RotorIntegrator integrator;

    @BeforeEach
    void setUp() {
        InductionSolver solver = new InductionSolver(
                new InflowGeometry(), new PolarInterpolator(),
                new PrandtlTipLoss(), new PrandtlHubLoss());
        integrator = new RotorIntegrator(solver);
    }

    private RotorSolution run(double tsr) {
        return integrator.integrate(TestFixtures.sampleRotor(), TestFixtures.polar(),
                tsr, 3, true, true, TestFixtures.HUB);
    }

    @Test
    @DisplayName("λ∈[1.5,10] 任取：Cp 为正且严格低于贝兹极限")
    void cpPositiveAndBelowBetzAcrossWholeRange() {
        for (double tsr = 1.5; tsr <= 10.0001; tsr += 0.25) {
            RotorSolution sol = run(tsr);
            assertTrue(sol.powerCoefficient() > 0.0,
                    "λ=" + tsr + " Cp 应为正：" + sol.powerCoefficient());
            assertTrue(sol.powerCoefficient() < RotorSolution.BETZ_LIMIT,
                    "λ=" + tsr + " Cp 不得超过贝兹极限：" + sol.powerCoefficient());
        }
    }

    @Test
    @DisplayName("从低 λ 抬向设计点：Cp 整体单调上升，不先跌负再穿零爬回")
    void cpRisesMonotonicallyTowardDesignPoint() {
        double previous = run(1.5).powerCoefficient();
        assertTrue(previous > 0.0, "λ=1.5 的 Cp 应为正：" + previous);
        for (double tsr = 1.75; tsr <= 6.0001; tsr += 0.25) {
            double cp = run(tsr).powerCoefficient();
            assertTrue(cp > previous,
                    "λ=" + tsr + " 的 Cp=" + cp + " 应高于上一档 " + previous);
            previous = cp;
        }
    }

    @Test
    @DisplayName("λ=2 与 λ=3：每个站点的微元扭矩 dCq 都为正")
    void elementTorquePositiveAtEveryStationAtLowTsR() {
        for (double tsr : new double[]{2.0, 3.0}) {
            RotorSolution sol = run(tsr);
            for (StationSolution s : sol.stations()) {
                assertTrue(s.dCq() > 0.0,
                        "λ=" + tsr + " μ=" + s.mu() + " 微元扭矩应为正：" + s.dCq());
            }
        }
    }

    @Test
    @DisplayName("λ=5/6/8/10 的 Cp、Ct 与既有基准一致（小数点后四位）")
    void designPointCoefficientsUnchanged() {
        double[][] expected = {
                // tsr, Cp, Ct
                {5.0, 0.4059, 0.6528},
                {6.0, 0.4170, 0.7189},
                {8.0, 0.3766, 0.7924},
                {10.0, 0.2708, 0.8272},
        };
        for (double[] row : expected) {
            RotorSolution sol = run(row[0]);
            assertEquals(row[1], sol.powerCoefficient(), 5e-5,
                    "λ=" + row[0] + " Cp 小数点后四位不得变");
            assertEquals(row[2], sol.thrustCoefficient(), 5e-5,
                    "λ=" + row[0] + " Ct 小数点后四位不得变");
        }
    }

    @Test
    @DisplayName("内联物理上很差的翼型表：照实计算，负 Cp 原样返回（不压零、不取绝对值）")
    void inlinePoorPolarComputedHonestly() {
        // 阻力远大于升力的劣质翼型：任何攻角下升阻比都很差，
        // 物理上必然得到负扭矩，服务不得把它修饰成非负
        AirfoilPolar poor = new AirfoilPolar("POOR",
                List.of(-180.0, 0.0, 180.0),
                List.of(0.8, 0.8, 0.8),
                List.of(1.5, 1.5, 1.5));
        RotorSolution sol = integrator.integrate(TestFixtures.sampleRotor(), poor,
                3.0, 3, true, true, TestFixtures.HUB);
        assertTrue(sol.powerCoefficient() < 0.0,
                "劣质翼型表算出的负 Cp 应照实返回：" + sol.powerCoefficient());
    }
}
