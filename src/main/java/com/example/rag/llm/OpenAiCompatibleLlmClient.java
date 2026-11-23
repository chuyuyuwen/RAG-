package com.example.rag.llm;

import com.example.rag.config.RagProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 调用 OpenAI 兼容的 <b>/chat/completions</b> 接口。
 *
 * <p>DeepSeek 的接口就是 OpenAI 格式，所以这个类同样适用于 OpenAI、硅基流动、
 * 通义千问、本地 vLLM / Ollama(OpenAI 兼容模式) 等 —— 只要改 base-url 和 model。
 *
 * <p>请求体：
 * <pre>
 * {
 *   "model": "deepseek-chat",
 *   "messages": [
 *     {"role":"system","content":"你是知识库助手……"},
 *     {"role":"user","content":"问题 + 检索到的资料"}
 *   ],
 *   "temperature": 0.2,
 *   "max_tokens": 1500
 * }
 * </pre>
 * 响应体里真正要的是 {@code choices[0].message.content}。
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final RestClient restClient;
    private final RagProperties.Llm cfg;

    public OpenAiCompatibleLlmClient(RagProperties.Llm cfg) {
        this.cfg = cfg;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 大模型生成本身就慢，读超时必须给足
        factory.setReadTimeout(Duration.ofSeconds(Math.max(30, cfg.getTimeoutSeconds())));

        this.restClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String chat(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new LlmException("消息列表不能为空。");
        }

        Map<String, Object> body = Map.of(
                "model", cfg.getModel(),
                "messages", messages,
                "temperature", cfg.getTemperature(),
                "max_tokens", cfg.getMaxTokens(),
                // 不流式：一次拿完整结果，实现最简单
                "stream", false);

        ChatResponse resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            String detail = e.getResponseBodyAsString();
            // 把常见的错误码翻译成用户能看懂的话
            String friendly = switch (code) {
                case 401 -> "API Key 无效或已过期，请检查 rag.llm.api-key。";
                case 402 -> "账户余额不足，请到 DeepSeek 平台充值。";
                case 429 -> "请求太频繁被限流了，稍等一会儿再试。";
                case 400 -> "请求参数有问题（常见原因：模型名写错、上下文超长）。";
                default -> "大模型接口返回错误。";
            };
            throw new LlmException(friendly + " HTTP " + code + "：" + detail, e);
        } catch (Exception e) {
            throw new LlmException("调用大模型接口失败：" + e.getMessage()
                    + "。请检查网络是否能访问 " + cfg.getBaseUrl(), e);
        }

        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
            throw new LlmException("大模型返回了空结果。");
        }

        ChatMessage message = resp.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new LlmException("大模型返回的回答是空的，可能是被安全策略拦截了。");
        }

        if (resp.usage() != null) {
            log.debug("token 用量：prompt={}, completion={}, total={}",
                    resp.usage().promptTokens(), resp.usage().completionTokens(), resp.usage().totalTokens());
        }
        return message.content();
    }

    @Override
    public String providerName() {
        return "openai-compatible:" + cfg.getModel();
    }

    /** 响应体。只声明我们需要的字段，其他字段 Jackson 会自动忽略。 */
    record ChatResponse(List<Choice> choices, Usage usage) {
    }

    record Choice(ChatMessage message) {
    }

    record ChatMessage(String role, String content) {
    }

    /**
     * token 用量统计。
     *
     * <p>必须用 {@code @JsonProperty} 显式指定名字：接口返回的是下划线风格
     * {@code prompt_tokens}，而 Java 字段是驼峰 {@code promptTokens}，
     * 不写注解的话 Jackson 会绑不上，字段永远是 null。
     */
    record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
