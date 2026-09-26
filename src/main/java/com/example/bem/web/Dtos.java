package com.example.bem.web;

import java.util.List;

/**
 * HTTP 请求/响应 DTO，与领域模型解耦；角度一律用度。
 */
public final class Dtos {

    private Dtos() {
    }

    public record ElementDto(Double mu, Double chord, Double twist) {
    }

    public record PolarDto(String name, List<Double> alphas, List<Double> cl, List<Double> cd) {
    }

    /** 整片风轮分析请求（两个分析接口共用）。 */
    public record AnalysisRequest(Double tipSpeedRatio,
                                  Integer bladeCount,
                                  List<ElementDto> elements,
                                  PolarDto polar,
                                  String polarName,
                                  Boolean useTipLoss,
                                  Boolean useHubLoss,
                                  Double hubRadius) {
    }

    /** 单叶素查询的额外定位信息：默认取叶中站点。 */
    public record StationQuery(Integer stationIndex, Double mu) {
    }

    public record StationResult(double mu,
                                double chord,
                                double twist,
                                double inflowAngleDeg,
                                double angleOfAttackDeg,
                                double axialInduction,
                                double tangentialInduction,
                                double cl,
                                double cd,
                                double prandtlFactor,
                                int iterations,
                                boolean highInduction,
                                double dCt,
                                double dCq) {
    }

    /** 单叶素接口响应：该站诱导因子 + 整机积分系数。 */
    public record StationResponse(StationResult station,
                                  double powerCoefficient,
                                  double thrustCoefficient,
                                  double torqueCoefficient) {
    }

    /** 整片叶片接口响应：各半径处攻角、微元推力、微元扭矩 + 整机系数。 */
    public record RotorResponse(double powerCoefficient,
                                double thrustCoefficient,
                                double torqueCoefficient,
                                List<StationResult> stations) {
    }

    public record ErrorResponse(String code, String message) {
    }

    public record PolarSummary(String name, int points) {
    }
}
