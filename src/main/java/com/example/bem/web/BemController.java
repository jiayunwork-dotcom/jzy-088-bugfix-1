package com.example.bem.web;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.RotorDefinition;
import com.example.bem.domain.RotorSolution;
import com.example.bem.domain.StationSolution;
import com.example.bem.sample.SampleAirfoilFactory;
import com.example.bem.sample.SampleRotorFactory;
import com.example.bem.registry.AirfoilRegistry;
import com.example.bem.service.AnalysisService;
import com.example.bem.validation.RequestValidator;
import com.example.bem.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * HTTP 层：对外能力只有两个分析接口（单叶素、整片叶片），
 * 外加翼型数据登记管理与内置算例查看。控制器不写任何气动公式。
 */
@RestController
@RequestMapping("/api")
public class BemController {

    private final AnalysisService analysisService;
    private final AirfoilRegistry registry;
    private final RequestValidator validator;
    private final SampleRotorFactory rotorFactory;
    private final SampleAirfoilFactory airfoilFactory;

    public BemController(AnalysisService analysisService,
                         AirfoilRegistry registry,
                         RequestValidator validator,
                         SampleRotorFactory rotorFactory,
                         SampleAirfoilFactory airfoilFactory) {
        this.analysisService = analysisService;
        this.registry = registry;
        this.validator = validator;
        this.rotorFactory = rotorFactory;
        this.airfoilFactory = airfoilFactory;
    }

    /**
     * 能力一：给单个叶素与叶尖速比，返回该处轴向/切向诱导因子，
     * 以及在此几何下整机积分得到的功率、推力系数。
     * 站点用 ?stationIndex= 或 ?mu= 指定，缺省取叶中站点。
     */
    @PostMapping("/analyze/station")
    public Dtos.StationResponse analyzeStation(@RequestBody Dtos.AnalysisRequest request,
                                               @RequestParam(required = false) Integer stationIndex,
                                               @RequestParam(required = false) Double mu) {
        RotorDefinition definition = analysisService.toDefinition(request);
        RotorSolution solution = analysisService.analyzeFull(definition);
        Dtos.StationQuery query = new Dtos.StationQuery(stationIndex, mu);
        int idx = analysisService.resolveStationIndex(
                solution.stations(), definition.elements(), query);
        StationSolution station = solution.stations().get(idx);
        return new Dtos.StationResponse(
                analysisService.toStationResult(station),
                solution.powerCoefficient(),
                solution.thrustCoefficient(),
                solution.torqueCoefficient());
    }

    /**
     * 能力二：给整片叶片的叶素序列，返回各半径处的攻角、微元推力、微元扭矩，
     * 以及整机功率、推力系数。
     */
    @PostMapping("/analyze/rotor")
    public Dtos.RotorResponse analyzeRotor(@RequestBody Dtos.AnalysisRequest request) {
        RotorDefinition definition = analysisService.toDefinition(request);
        RotorSolution solution = analysisService.analyzeFull(definition);
        List<Dtos.StationResult> stations = solution.stations().stream()
                .map(analysisService::toStationResult)
                .toList();
        return new Dtos.RotorResponse(
                solution.powerCoefficient(),
                solution.thrustCoefficient(),
                solution.torqueCoefficient(),
                stations);
    }

    // ---- 翼型数据登记管理 ----

    @GetMapping("/polars")
    public List<Dtos.PolarSummary> listPolars() {
        return registry.all().stream()
                .map(p -> new Dtos.PolarSummary(p.name(), p.alphas().size()))
                .sorted(java.util.Comparator.comparing(Dtos.PolarSummary::name))
                .toList();
    }

    @GetMapping("/polars/{name}")
    public Dtos.PolarDto getPolar(@PathVariable String name) {
        AirfoilPolar polar = registry.find(name)
                .orElseThrow(() -> new ValidationException("POLAR_NOT_FOUND",
                        "未找到名为 '" + name + "' 的登记翼型"));
        return new Dtos.PolarDto(polar.name(), polar.alphas(), polar.cl(), polar.cd());
    }

    @PostMapping("/polars")
    public ResponseEntity<Dtos.PolarSummary> registerPolar(@RequestBody Dtos.PolarDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ValidationException("POLAR_NAME_MISSING", "登记翼型必须提供 name");
        }
        AirfoilPolar polar = new AirfoilPolar(dto.name(), dto.alphas(), dto.cl(), dto.cd());
        validator.validatePolar(polar);
        boolean existed = registry.register(polar).isPresent();
        Dtos.PolarSummary summary = new Dtos.PolarSummary(polar.name(), polar.alphas().size());
        if (existed) {
            return ResponseEntity.ok(summary);
        }
        return ResponseEntity.created(URI.create("/api/polars/" + polar.name())).body(summary);
    }

    @DeleteMapping("/polars/{name}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolar(@PathVariable String name) {
        if (!registry.remove(name)) {
            throw new ValidationException("POLAR_NOT_FOUND",
                    "未找到名为 '" + name + "' 的登记翼型");
        }
    }

    /** 内置三叶片算例几何，便于调用方手工核对。 */
    @GetMapping("/samples/rotor")
    public Dtos.AnalysisRequest sampleRotor() {
        List<Dtos.ElementDto> elements = rotorFactory.build().stream()
                .map(e -> new Dtos.ElementDto(e.mu(), e.chord(), e.twist()))
                .toList();
        return new Dtos.AnalysisRequest(
                SampleRotorFactory.DESIGN_TSR,
                SampleRotorFactory.DESIGN_BLADES,
                elements, null,
                SampleAirfoilFactory.SAMPLE_POLAR_NAME, true, true,
                SampleRotorFactory.HUB_RADIUS);
    }
}
