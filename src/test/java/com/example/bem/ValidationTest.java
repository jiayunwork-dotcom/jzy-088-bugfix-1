package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.domain.BladeElement;
import com.example.bem.validation.RequestValidator;
import com.example.bem.validation.ValidationException;
import com.example.bem.registry.AirfoilRegistry;
import com.example.bem.domain.RotorDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 非法输入校验测试：每条规则对应一个稳定机器码。
 */
class ValidationTest {

    private RequestValidator validator;

    private static RotorDefinition def(double tsr, int blades, List<BladeElement> el,
                                       AirfoilPolar polar, String polarName) {
        return new RotorDefinition(tsr, blades, el, polar, polarName, true, true, 0.20);
    }

    @BeforeEach
    void setUp() {
        validator = new RequestValidator(new AirfoilRegistry());
    }

    @Test
    @DisplayName("叶尖速比为零/负/非有限 -> TSR_NOT_POSITIVE")
    void tsrMustBePositive() {
        List<BladeElement> el = TestFixtures.sampleRotor();
        for (double bad : new double[]{0.0, -1.0, Double.NaN}) {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> validator.validate(def(bad, 3, el, TestFixtures.polar(), null)));
            assertEquals("TSR_NOT_POSITIVE", ex.code());
            assertTrue(ex.getMessage().contains("叶尖速比"));
        }
    }

    @Test
    @DisplayName("叶片数非正整数 -> BLADE_COUNT_NOT_POSITIVE")
    void bladeCountMustBePositive() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 0, TestFixtures.sampleRotor(),
                        TestFixtures.polar(), null)));
        assertEquals("BLADE_COUNT_NOT_POSITIVE", ex.code());
    }

    @Test
    @DisplayName("径向站点少于三个 -> ELEMENTS_TOO_FEW")
    void atLeastThreeStations() {
        List<BladeElement> two = TestFixtures.sampleRotor().subList(0, 2);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 3, two, TestFixtures.polar(), null)));
        assertEquals("ELEMENTS_TOO_FEW", ex.code());
    }

    @Test
    @DisplayName("翼型系数表为空 -> POLAR_EMPTY")
    void polarMustNotBeEmpty() {
        AirfoilPolar empty = new AirfoilPolar("x", new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validatePolar(empty));
        assertEquals("POLAR_EMPTY", ex.code());
    }

    @Test
    @DisplayName("径向坐标非单调 -> MU_NOT_MONOTONIC")
    void muMustBeMonotonic() {
        List<BladeElement> good = new ArrayList<>(TestFixtures.sampleRotor());
        good.set(4, new BladeElement(good.get(3).mu(), 0.1, 5.0)); // 与前一站重复
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 3, good, TestFixtures.polar(), null)));
        assertEquals("MU_NOT_MONOTONIC", ex.code());
    }

    @Test
    @DisplayName("弦长非正 -> CHORD_NOT_POSITIVE")
    void chordMustBePositive() {
        List<BladeElement> bad = new ArrayList<>(TestFixtures.sampleRotor());
        bad.set(2, new BladeElement(bad.get(2).mu(), 0.0, bad.get(2).twist()));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 3, bad, TestFixtures.polar(), null)));
        assertEquals("CHORD_NOT_POSITIVE", ex.code());
    }

    @Test
    @DisplayName("引用未登记翼型名 -> POLAR_NOT_FOUND")
    void unknownPolarNameRejected() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 3, TestFixtures.sampleRotor(),
                        null, "NO-SUCH-AIRFOIL")));
        assertEquals("POLAR_NOT_FOUND", ex.code());
    }

    @Test
    @DisplayName("极曲线与内联表都没给 -> POLAR_MISSING")
    void polarMissing() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(def(6.0, 3, TestFixtures.sampleRotor(), null, null)));
        assertEquals("POLAR_MISSING", ex.code());
    }

    @Test
    @DisplayName("合法定义通过校验")
    void validDefinitionPasses() {
        validator.validate(def(6.0, 3, TestFixtures.sampleRotor(), TestFixtures.polar(), null));
    }
}
