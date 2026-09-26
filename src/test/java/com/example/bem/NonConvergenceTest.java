package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.inflow.InflowGeometry;
import com.example.bem.integration.RotorIntegrator;
import com.example.bem.induction.InductionSolver;
import com.example.bem.induction.NonConvergentException;
import com.example.bem.polar.PolarInterpolator;
import com.example.bem.tiploss.PrandtlHubLoss;
import com.example.bem.tiploss.PrandtlTipLoss;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不收敛必须报错，绝不返回看似有值的发散结果。
 *
 * 用极端宽弦（极大当地实度）构造一个把动量方程推出物理区间、
 * 迭代无法在限定轮数内稳定的工况。
 */
class NonConvergenceTest {

    @Test
    @DisplayName("极端实度工况迭代不收敛时抛 NonConvergentException")
    void divergentCaseRaises() {
        InductionSolver solver = new InductionSolver(
                new InflowGeometry(), new PolarInterpolator(),
                new PrandtlTipLoss(), new PrandtlHubLoss());
        RotorIntegrator integrator = new RotorIntegrator(solver);

        // 弦长大到 c/R ≈ 1.5，实度严重超出现实范围
        List<BladeElement> huge = TestFixtures.uniformRotor(1.5, 30.0, 0.0, 9);
        AirfoilPolar polar = TestFixtures.polar();

        NonConvergentException ex = assertThrows(NonConvergentException.class,
                () -> integrator.integrate(huge, polar, 6.0, 3, true, true, 0.20));
        // 报错必须指明是哪个站点、为什么没收敛
        assertTrue(ex.getMessage().contains("mu=") || ex.getMessage().contains("收敛"));
    }
}
