package com.example.bem;

import com.example.bem.inflow.InflowGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 入流角/攻角纯几何关系单测。 */
class InflowGeometryTest {

    private final InflowGeometry geom = new InflowGeometry();

    @Test
    @DisplayName("tan(φ) = (1-a)/((1+a')·λ·μ)")
    void inflowAngleMatchesVelocityTriangle() {
        double phi = geom.inflowAngleRad(0.25, 0.1, 6.0, 0.7);
        double expected = Math.atan((1 - 0.25) / ((1 + 0.1) * 6.0 * 0.7));
        assertEquals(expected, phi, 1e-12);
    }

    @Test
    @DisplayName("a=0,a'=0,μ=1 时 φ=arctan(1/λ)")
    void simpleInflowAtTip() {
        assertEquals(Math.atan(1.0 / 6.0), geom.inflowAngleRad(0, 0, 6, 1), 1e-12);
    }

    @Test
    @DisplayName("攻角 α = φ(度) − twist")
    void angleOfAttackSubtractsTwist() {
        double phi = geom.inflowAngleRad(0.0, 0.0, 6.0, 1.0);
        double alpha = geom.angleOfAttackDeg(phi, 3.0);
        assertEquals(Math.toDegrees(phi) - 3.0, alpha, 1e-12);
    }

    @Test
    @DisplayName("a 趋近 1 时入流角趋于 0 而非变负")
    void inflowStaysNonNegative() {
        assertTrue(geom.inflowAngleRad(0.999, 0.0, 6.0, 0.5) >= 0.0);
    }
}
