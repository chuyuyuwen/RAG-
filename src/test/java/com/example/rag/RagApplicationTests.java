package com.example.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

/**
 * 冒烟测试：确认 Spring 容器能起来，且 /api/health 返回 200。
 *
 * <p>webEnvironment = RANDOM_PORT 会真的启动一个 Tomcat（随机端口），
 * 这样才能验证完整的 HTTP 链路，而不是只测 Bean 装配。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RagApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void 健康检查接口可用() {
        ResponseEntity<String> resp = rest.getForEntity("/api/health", String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("UP");
    }

    @Test
    void 首页可以访问() {
        ResponseEntity<String> resp = rest.getForEntity("/", String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("RAG 知识库");
    }
}
