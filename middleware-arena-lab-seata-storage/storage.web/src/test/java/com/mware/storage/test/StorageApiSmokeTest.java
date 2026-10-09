package com.mware.storage.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Storage 服务真实 HTTP 接口冒烟测试。 */
class StorageApiSmokeTest {

    private static final String ENABLE_PROPERTY = "ma.it.enabled";
    private static final String BASE_URL_PROPERTY = "ma.it.storage.base-url";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:9007";

    @Test
    void healthEndpointShouldBeAvailable() throws Exception {
        // 普通 Maven build 不要求服务已经启动；联调阶段显式传 -Dma.it.enabled=true。
        Assumptions.assumeTrue(
                Boolean.getBoolean(ENABLE_PROPERTY),
                "未开启真实 HTTP 接口测试"
        );

        String baseUrl = System.getProperty(BASE_URL_PROPERTY, DEFAULT_BASE_URL);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }
}
