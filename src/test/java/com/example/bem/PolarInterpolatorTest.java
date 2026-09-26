package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.polar.PolarInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 翼型表分段线性插值单测。 */
class PolarInterpolatorTest {

    private final PolarInterpolator interp = new PolarInterpolator();

    private AirfoilPolar polar() {
        return new AirfoilPolar("t",
                List.of(0.0, 10.0, 20.0),
                List.of(0.0, 1.0, 0.5),
                List.of(0.01, 0.02, 0.10));
    }

    @Test
    @DisplayName("表内中点线性插值")
    void midpointLinear() {
        PolarInterpolator.Coefficients c = interp.interpolate(polar(), 5.0);
        assertEquals(0.5, c.cl(), 1e-12);
        assertEquals(0.015, c.cd(), 1e-12);
    }

    @Test
    @DisplayName("精确命中表点")
    void exactNode() {
        PolarInterpolator.Coefficients c = interp.interpolate(polar(), 10.0);
        assertEquals(1.0, c.cl(), 1e-12);
        assertEquals(0.02, c.cd(), 1e-12);
    }

    @Test
    @DisplayName("超出表范围钳制到端点")
    void clampOutside() {
        assertEquals(0.0, interp.interpolate(polar(), -50.0).cl(), 1e-12);
        assertEquals(0.5, interp.interpolate(polar(), 999.0).cl(), 1e-12);
    }
}
