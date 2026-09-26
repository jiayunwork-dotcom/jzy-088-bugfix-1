package com.example.bem;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.registry.AirfoilRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 翼型登记处管理单测。 */
class AirfoilRegistryTest {

    private AirfoilPolar polar(String name) {
        return new AirfoilPolar(name, List.of(0.0, 5.0), List.of(0.2, 0.7), List.of(0.01, 0.01));
    }

    @Test
    @DisplayName("登记后可按名字取回，找不到返回 empty")
    void registerAndFind() {
        AirfoilRegistry reg = new AirfoilRegistry();
        assertTrue(reg.find("X").isEmpty());
        reg.register(polar("X"));
        assertTrue(reg.find("X").isPresent());
        assertEquals(1, reg.all().size());
    }

    @Test
    @DisplayName("同名覆盖返回旧表")
    void overwriteReturnsOld() {
        AirfoilRegistry reg = new AirfoilRegistry();
        assertTrue(reg.register(polar("X")).isEmpty());
        assertTrue(reg.register(polar("X")).isPresent());
    }

    @Test
    @DisplayName("删除")
    void remove() {
        AirfoilRegistry reg = new AirfoilRegistry();
        reg.register(polar("X"));
        assertTrue(reg.remove("X"));
        assertFalse(reg.remove("X"));
    }
}
