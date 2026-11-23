package com.example.rag.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>@RestController = @Controller + @ResponseBody，方法返回值会被自动转成 JSON 写回响应体。
 * 它的作用是让我们在还没做业务接口时，就能确认「服务起来了、能对外提供 HTTP 服务」。
 */
@RestController
public class HealthController {

    /** GET /api/health → {"status":"UP","time":"..."} */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "rag",
                "time", Instant.now().toString());
    }
}
