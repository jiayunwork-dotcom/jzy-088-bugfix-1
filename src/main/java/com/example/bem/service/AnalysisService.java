package com.example.bem.service;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.domain.RotorDefinition;
import com.example.bem.domain.RotorSolution;
import com.example.bem.domain.StationSolution;
import com.example.bem.integration.RotorIntegrator;
import com.example.bem.registry.AirfoilRegistry;
import com.example.bem.validation.RequestValidator;
import com.example.bem.validation.ValidationException;
import com.example.bem.web.Dtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排层：把 HTTP DTO 翻成领域对象，校验后调用积分模块，再把结果翻回 DTO。
 * 它本身不含迭代公式、不含积分公式，避免“内核与逻辑堆进一个类”。
 */
@Service
public class AnalysisService {

    private final RequestValidator validator;
    private final AirfoilRegistry registry;
    private final RotorIntegrator integrator;

    public AnalysisService(RequestValidator validator,
                           AirfoilRegistry registry,
                           RotorIntegrator integrator) {
        this.validator = validator;
        this.registry = registry;
        this.integrator = integrator;
    }

    /** 整片叶片分析。 */
    public RotorSolution analyze(RotorDefinition definition) {
        validator.validate(definition);
        AirfoilPolar polar = resolvePolar(definition);
        return integrator.integrate(definition.elements(), polar,
                definition.tipSpeedRatio(), definition.bladeCount(),
                definition.useTipLoss(), definition.useHubLoss(), definition.hubRadius());
    }

    /** 完整解（controller 用于同时拿到指定站点与整机积分）。 */
    public RotorSolution analyzeFull(RotorDefinition definition) {
        return analyze(definition);
    }

    public int resolveStationIndex(List<StationSolution> stations,
                                   List<BladeElement> elements,
                                   Dtos.StationQuery query) {
        return selectIndex(stations, elements, query);
    }

    private int selectIndex(List<StationSolution> stations,
                            List<BladeElement> elements,
                            Dtos.StationQuery query) {
        if (query == null) {
            return stations.size() / 2;
        }
        if (query.stationIndex() != null) {
            int idx = query.stationIndex();
            if (idx < 0 || idx >= stations.size()) {
                throw new ValidationException("STATION_INDEX_OUT_OF_RANGE",
                        "stationIndex 必须在 [0," + (stations.size() - 1) + "]，收到 " + idx);
            }
            return idx;
        }
        if (query.mu() != null) {
            double target = query.mu();
            int best = 0;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < elements.size(); i++) {
                double d = Math.abs(elements.get(i).mu() - target);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            return best;
        }
        return stations.size() / 2;
    }

    public AirfoilPolar resolvePolar(RotorDefinition definition) {
        if (definition.polar() != null) {
            return definition.polar();
        }
        return registry.find(definition.polarName())
                .orElseThrow(() -> new ValidationException("POLAR_NOT_FOUND",
                        "未找到名为 '" + definition.polarName() + "' 的登记翼型"));
    }

    // ---- DTO 映射 ----

    public RotorDefinition toDefinition(Dtos.AnalysisRequest dto) {
        if (dto == null) {
            throw new ValidationException("BODY_MISSING", "请求体为空或不是合法 JSON");
        }
        List<BladeElement> elements = new ArrayList<>();
        if (dto.elements() != null) {
            for (Dtos.ElementDto e : dto.elements()) {
                if (e == null || e.mu() == null || e.chord() == null || e.twist() == null) {
                    throw new ValidationException("ELEMENT_FIELD_MISSING",
                            "每个叶素必须同时给出 mu、chord、twist");
                }
                elements.add(new BladeElement(e.mu(), e.chord(), e.twist()));
            }
        }
        AirfoilPolar polar = null;
        if (dto.polar() != null) {
            polar = new AirfoilPolar(dto.polar().name(), dto.polar().alphas(),
                    dto.polar().cl(), dto.polar().cd());
        }
        double tsr = dto.tipSpeedRatio() == null ? Double.NaN : dto.tipSpeedRatio();
        int blades = dto.bladeCount() == null ? 0 : dto.bladeCount();
        boolean tipLoss = dto.useTipLoss() == null || dto.useTipLoss();
        boolean hubLoss = dto.useHubLoss() == null || dto.useHubLoss();
        double hubRadius = dto.hubRadius() == null ? 0.2 : dto.hubRadius();
        return new RotorDefinition(tsr, blades, elements, polar, dto.polarName(),
                tipLoss, hubLoss, hubRadius);
    }

    public Dtos.StationResult toStationResult(StationSolution s) {
        return new Dtos.StationResult(
                s.mu(), s.chord(), s.twist(),
                s.inflowAngle(), s.angleOfAttack(),
                s.axialInduction(), s.tangentialInduction(),
                s.cl(), s.cd(), s.prandtlFactor(),
                s.iterations(), s.highInduction(),
                s.dCt(), s.dCq());
    }
}
