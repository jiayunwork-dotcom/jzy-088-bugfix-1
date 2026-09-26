package com.example.bem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 容器健康检查用的极简探针（仅容器 HEALTHCHECK 调用，不是业务模块）：
 * 请求本地翼型登记列表接口，2xx 即视为存活。
 */
public final class HealthCheck {

    private HealthCheck() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/polars"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            int code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code >= 200 && code < 300) {
                System.exit(0);
            }
            System.err.println("health check status " + code);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("health check failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
