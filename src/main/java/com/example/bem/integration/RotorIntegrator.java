package com.example.bem.integration;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.domain.RotorSolution;
import com.example.bem.domain.StationSolution;
import com.example.bem.induction.InductionSolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 逐叶素求解并沿叶展做数值积分。
 *
 * 载荷取叶素侧表达式（以远前方风速 U、风轮半径 R 无量纲化）：
 *   dCt/dμ = (B chord/π) (1-a)² Cn / sin²φ
 *   dCq/dμ = (B chord/π) μ (1-a)² Ct / sin²φ
 * 积分用梯形公式：
 *   Ct = ∫ dCt/dμ dμ，Cq = ∫ dCq/dμ dμ，Cp = λ·Cq
 *
 * 本类只负责“逐站调用迭代内核 + 拼微元载荷 + 梯形积分”，
 * 诱导因子怎么迭代完全在 {@link InductionSolver} 内，二者不耦合在一个类里。
 */
@Component
public class RotorIntegrator {

    private final InductionSolver solver;

    public RotorIntegrator(InductionSolver solver) {
        this.solver = solver;
    }

    /** 对整片风轮逐叶素求解并积分。 */
    public RotorSolution integrate(List<BladeElement> elements, AirfoilPolar polar,
                                   double lambda, int blades, boolean useTipLoss,
                                   boolean useHubLoss, double hubRadius) {
        List<StationSolution> stations = new ArrayList<>(elements.size());
        for (BladeElement element : elements) {
            InductionSolver.ConvergedStation s = solver.solve(
                    element, polar, lambda, blades, useTipLoss, hubRadius, useHubLoss);
            stations.add(buildStation(element, s, lambda, blades));
        }

        double ct = trapezoid(stations, true);
        double cq = trapezoid(stations, false);
        double cp = lambda * cq;
        return new RotorSolution(cp, ct, cq, stations);
    }

    private StationSolution buildStation(BladeElement element,
                                         InductionSolver.ConvergedStation s,
                                         double lambda, int blades) {
        double sinPhi = Math.sin(s.phiRad());
        double cosPhi = Math.cos(s.phiRad());
        double cn = s.cl() * cosPhi + s.cd() * sinPhi;
        double ct = s.cl() * sinPhi - s.cd() * cosPhi;
        double oneMinusA = 1.0 - s.a();

        double dCt = (blades * element.chord() / Math.PI)
                * oneMinusA * oneMinusA * cn / (sinPhi * sinPhi);
        double dCq = (blades * element.chord() / Math.PI) * element.mu()
                * oneMinusA * oneMinusA * ct / (sinPhi * sinPhi);

        return new StationSolution(
                element.mu(), element.chord(), element.twist(),
                Math.toDegrees(s.phiRad()), s.alphaDeg(),
                s.a(), s.ap(), s.cl(), s.cd(), s.f(),
                s.iterations(), s.highInduction(), dCt, dCq);
    }

    private double trapezoid(List<StationSolution> stations, boolean thrust) {
        double sum = 0.0;
        for (int i = 1; i < stations.size(); i++) {
            StationSolution p = stations.get(i - 1);
            StationSolution c = stations.get(i);
            double yp = thrust ? p.dCt() : p.dCq();
            double yc = thrust ? c.dCt() : c.dCq();
            sum += 0.5 * (yp + yc) * (c.mu() - p.mu());
        }
        return sum;
    }
}
