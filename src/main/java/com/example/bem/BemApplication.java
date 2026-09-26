package com.example.bem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 叶素动量（BEM）水平轴风轮气动核算服务入口。
 * 服务只提供 HTTP JSON 接口，不含前端页面，也不做登录鉴权。
 */
@SpringBootApplication
public class BemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BemApplication.class, args);
    }
}
