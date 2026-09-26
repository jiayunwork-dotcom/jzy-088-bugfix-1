package com.example.bem.validation;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.domain.RotorDefinition;
import com.example.bem.registry.AirfoilRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 请求输入校验：只做合法性判断，不做任何气动计算。
 *
 * 规则（与需求一一对应）：
 *  - 叶尖速比必须为正
 *  - 叶片数必须为正整数
 *  - 径向站点至少 3 个，径向坐标严格单调递增
 *  - 叶素弦长为正、0 &lt; mu &le; 1
 *  - 翼型表：内联时非空、至少两个点、攻角单调无重复、Cl/Cd 等长且有限
 *    按名字引用时必须已登记
 */
@Component
public class RequestValidator {

    private final AirfoilRegistry registry;

    public RequestValidator(AirfoilRegistry registry) {
        this.registry = registry;
    }

    public void validate(RotorDefinition req) {
        if (!(req.tipSpeedRatio() > 0.0) || !Double.isFinite(req.tipSpeedRatio())) {
            throw new ValidationException("TSR_NOT_POSITIVE",
                    "叶尖速比必须为正数，收到: " + req.tipSpeedRatio());
        }
        if (req.bladeCount() <= 0) {
            throw new ValidationException("BLADE_COUNT_NOT_POSITIVE",
                    "叶片数必须为正整数，收到: " + req.bladeCount());
        }
        List<BladeElement> elements = req.elements();
        if (elements == null || elements.isEmpty()) {
            throw new ValidationException("ELEMENTS_EMPTY", "叶素序列不能为空");
        }
        if (elements.size() < 3) {
            throw new ValidationException("ELEMENTS_TOO_FEW",
                    "径向站点至少需要 3 个，收到 " + elements.size() + " 个");
        }

        double prevMu = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < elements.size(); i++) {
            BladeElement e = elements.get(i);
            if (e == null) {
                throw new ValidationException("ELEMENT_NULL",
                        "第 " + i + " 个叶素为空");
            }
            if (!(e.mu() > 0.0 && e.mu() <= 1.0) || !Double.isFinite(e.mu())) {
                throw new ValidationException("MU_OUT_OF_RANGE",
                        "第 " + i + " 个叶素径向位置 r/R 必须落在 (0,1]，收到 " + e.mu());
            }
            if (!(e.chord() > 0.0) || !Double.isFinite(e.chord())) {
                throw new ValidationException("CHORD_NOT_POSITIVE",
                        "第 " + i + " 个叶素弦长必须为正，收到 " + e.chord());
            }
            if (!Double.isFinite(e.twist())) {
                throw new ValidationException("TWIST_INVALID",
                        "第 " + i + " 个叶素扭角非有限值");
            }
            if (e.mu() <= prevMu) {
                throw new ValidationException("MU_NOT_MONOTONIC",
                        "径向坐标必须严格单调递增，第 " + (i - 1) + "、" + i
                                + " 个站点分别为 " + prevMu + "、" + e.mu());
            }
            prevMu = e.mu();
        }

        if (!Double.isFinite(req.hubRadius()) || req.hubRadius() < 0.0
                || req.hubRadius() >= elements.get(0).mu()) {
            throw new ValidationException("HUB_RADIUS_INVALID",
                    "根切半径 hubRadius 必须满足 0 ≤ hubRadius < 首个叶素半径（"
                            + elements.get(0).mu() + "），收到 " + req.hubRadius());
        }

        if (req.polar() != null) {
            validatePolar(req.polar());
        } else if (req.polarName() != null && !req.polarName().isBlank()) {
            if (registry.find(req.polarName()).isEmpty()) {
                throw new ValidationException("POLAR_NOT_FOUND",
                        "未找到名为 '" + req.polarName() + "' 的登记翼型");
            }
        } else {
            throw new ValidationException("POLAR_MISSING",
                    "必须内联提供翼型表（polar）或引用已登记翼型名（polarName）");
        }
    }

    /** 翼型表自身的结构校验（登记与内联请求共用）。 */
    public void validatePolar(AirfoilPolar polar) {
        if (polar == null) {
            throw new ValidationException("POLAR_EMPTY", "翼型系数表为空");
        }
        List<Double> alphas = polar.alphas();
        List<Double> cl = polar.cl();
        List<Double> cd = polar.cd();
        if (alphas == null || alphas.isEmpty()) {
            throw new ValidationException("POLAR_EMPTY", "翼型系数表为空");
        }
        if (alphas.size() < 2) {
            throw new ValidationException("POLAR_TOO_FEW_POINTS",
                    "翼型系数表至少需要 2 个攻角点，收到 " + alphas.size() + " 个");
        }
        if (cl == null || cd == null || cl.size() != alphas.size() || cd.size() != alphas.size()) {
            throw new ValidationException("POLAR_LENGTH_MISMATCH",
                    "翼型表 alphas/cl/cd 长度必须一致");
        }
        double prev = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < alphas.size(); i++) {
            double a = alphas.get(i);
            if (!Double.isFinite(a) || !Double.isFinite(cl.get(i)) || !Double.isFinite(cd.get(i))) {
                throw new ValidationException("POLAR_NON_FINITE",
                        "翼型表第 " + i + " 行含非有限值");
            }
            if (a <= prev) {
                throw new ValidationException("POLAR_ALPHA_NOT_MONOTONIC",
                        "翼型表攻角必须严格单调递增（第 " + i + " 个点 " + a + "）");
            }
            prev = a;
        }
    }
}
