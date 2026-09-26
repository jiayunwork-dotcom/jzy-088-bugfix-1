package com.example.bem.registry;

import com.example.bem.domain.AirfoilPolar;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翼型气动数据登记处：把一张翼型极曲线以名字登记，供多次分析引用。
 *
 * 这只是数据的组织方式（内存实现，线程安全），服务核心仍是诱导因子迭代；
 * 分析请求既可以引用已登记名字，也可以直接内联一张表。
 */
@Component
public class AirfoilRegistry {

    private final ConcurrentHashMap<String, AirfoilPolar> store = new ConcurrentHashMap<>();

    /** 登记（同名覆盖，返回旧表；首次登记返回 empty）。 */
    public Optional<AirfoilPolar> register(AirfoilPolar polar) {
        AirfoilPolar named = new AirfoilPolar(polar.name(), polar.alphas(), polar.cl(), polar.cd());
        return Optional.ofNullable(store.put(named.name(), named));
    }

    public Optional<AirfoilPolar> find(String name) {
        return Optional.ofNullable(store.get(name));
    }

    public Collection<AirfoilPolar> all() {
        return store.values();
    }

    /** 删除；不存在返回 false。 */
    public boolean remove(String name) {
        return store.remove(name) != null;
    }
}
