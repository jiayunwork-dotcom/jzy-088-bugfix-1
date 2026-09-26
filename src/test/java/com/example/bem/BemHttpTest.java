package com.example.bem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 端到端测试：两个分析接口、非法输入机器码、不收敛错误码、
 * 内置算例与翼型登记管理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BemHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper mapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** 直接取内置算例请求体（已引用登记翼型名）。 */
    private String sampleRequestBody() throws Exception {
        ResponseEntity<String> sample = rest.getForEntity(url("/api/samples/rotor"), String.class);
        assertEquals(HttpStatus.OK, sample.getStatusCode());
        return sample.getBody();
    }

    @Test
    @DisplayName("能力二 /analyze/rotor：返回各站攻角、微元推力、微元扭矩与整机系数")
    void rotorAnalysis() throws Exception {
        String body = sampleRequestBody();
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/rotor"), new HttpEntity<>(body, json()), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode root = mapper.readTree(resp.getBody());
        assertTrue(root.get("powerCoefficient").asDouble() > 0.35);
        assertTrue(root.get("powerCoefficient").asDouble() < 16.0 / 27.0);
        assertTrue(root.get("thrustCoefficient").asDouble() > 0);
        JsonNode stations = root.get("stations");
        assertTrue(stations.size() >= 3);
        for (JsonNode s : stations) {
            assertNotNull(s.get("angleOfAttackDeg").asDouble());
            assertTrue(Double.isFinite(s.get("dCt").asDouble()));
            assertTrue(Double.isFinite(s.get("dCq").asDouble()));
            assertTrue(s.get("axialInduction").asDouble() >= 0);
        }
    }

    @Test
    @DisplayName("能力一 /analyze/station：返回该站 a、a' 与整机功率、推力系数")
    void stationAnalysis() throws Exception {
        String body = sampleRequestBody();
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/station?stationIndex=4"),
                new HttpEntity<>(body, json()), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode root = mapper.readTree(resp.getBody());
        JsonNode st = root.get("station");
        assertTrue(st.get("axialInduction").asDouble() > 0);
        assertTrue(st.get("tangentialInduction").asDouble() >= 0);
        assertTrue(root.get("powerCoefficient").asDouble() > 0);
        assertTrue(root.get("thrustCoefficient").asDouble() > 0);
    }

    @Test
    @DisplayName("非法叶尖速比返回 400 与机器码 TSR_NOT_POSITIVE")
    void invalidTsr() throws Exception {
        String body = sampleRequestBody().replace("\"tipSpeedRatio\":6.0",
                "\"tipSpeedRatio\":-2");
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/rotor"), new HttpEntity<>(body, json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        JsonNode root = mapper.readTree(resp.getBody());
        assertEquals("TSR_NOT_POSITIVE", root.get("code").asText());
        assertTrue(root.get("message").asText().length() > 0);
    }

    @Test
    @DisplayName("径向站点不足三个返回 400 与机器码 ELEMENTS_TOO_FEW")
    void tooFewStations() throws Exception {
        String body = sampleRequestBody();
        JsonNode req = mapper.readTree(body);
        JsonNode elements = req.get("elements");
        com.fasterxml.jackson.databind.node.ArrayNode arr =
                mapper.createArrayNode();
        arr.add(elements.get(0)).add(elements.get(1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) req).set("elements", arr);
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/rotor"),
                new HttpEntity<>(mapper.writeValueAsString(req), json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("ELEMENTS_TOO_FEW",
                mapper.readTree(resp.getBody()).get("code").asText());
    }

    @Test
    @DisplayName("非法/畸形 JSON 返回机器码而非 500")
    void malformedJson() {
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/rotor"),
                new HttpEntity<>("{not json", json()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().contains("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("引用不存在的登记翼型返回 404 POLAR_NOT_FOUND")
    void unknownPolar() throws Exception {
        String body = sampleRequestBody().replace(
                "\"polarName\":\"NACA4412-SAMPLE\"", "\"polarName\":\"GHOST\"");
        ResponseEntity<String> resp = rest.postForEntity(
                url("/api/analyze/rotor"), new HttpEntity<>(body, json()), String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("POLAR_NOT_FOUND",
                mapper.readTree(resp.getBody()).get("code").asText());
    }

    @Test
    @DisplayName("翼型登记：新增->查询->删除全生命周期")
    void polarLifecycle() {
        String payload = "{\"name\":\"UNIT-TEST-POLAR\",\"alphas\":[0,5,10],"
                + "\"cl\":[0.2,0.7,1.1],\"cd\":[0.01,0.01,0.02]}";
        ResponseEntity<String> created = rest.postForEntity(
                url("/api/polars"), new HttpEntity<>(payload, json()), String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<String> got = rest.getForEntity(
                url("/api/polars/UNIT-TEST-POLAR"), String.class);
        assertEquals(HttpStatus.OK, got.getStatusCode());
        assertTrue(got.getBody().contains("UNIT-TEST-POLAR"));

        rest.delete(url("/api/polars/UNIT-TEST-POLAR"));
        ResponseEntity<String> missing = rest.getForEntity(
                url("/api/polars/UNIT-TEST-POLAR"), String.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    @DisplayName("启动时内置示例翼型已登记")
    void samplePolarRegistered() {
        ResponseEntity<String> list = rest.getForEntity(url("/api/polars"), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertTrue(list.getBody().contains("NACA4412-SAMPLE"));
    }
}
