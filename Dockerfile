# ---- 构建阶段：现成的 Maven 3.9 + Temurin JDK 17 官方镜像 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先拷 pom 预热依赖层，再拷源码，利用构建缓存
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段：仅 JRE，单容器直接对外 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 容器内以非 root 用户运行
RUN groupadd --system bem && useradd --system --gid bem --no-create-home bem
COPY --from=build /build/target/bem-rotor-service-1.0.0.jar app.jar
USER bem

EXPOSE 8080
# 随包 HealthCheck 探针经 Spring Boot PropertiesLauncher 调用，无需在镜像内装 curl
HEALTHCHECK --interval=15s --timeout=4s --start-period=20s --retries=5 \
    CMD ["java", "-cp", "/app/app.jar", "-Dloader.main=com.example.bem.HealthCheck", "org.springframework.boot.loader.launch.PropertiesLauncher"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
