package com.example.bem.sample;

import com.example.bem.domain.AirfoilPolar;
import com.example.bem.registry.AirfoilRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 启动时把内置示例翼型登记进注册表，使其可按名字被分析接口直接引用。
 */
@Component
public class SampleDataSeeder {

    private final AirfoilRegistry registry;
    private final SampleAirfoilFactory airfoilFactory;

    public SampleDataSeeder(AirfoilRegistry registry, SampleAirfoilFactory airfoilFactory) {
        this.registry = registry;
        this.airfoilFactory = airfoilFactory;
    }

    @PostConstruct
    void seed() {
        AirfoilPolar polar = airfoilFactory.build();
        registry.register(polar);
    }
}
