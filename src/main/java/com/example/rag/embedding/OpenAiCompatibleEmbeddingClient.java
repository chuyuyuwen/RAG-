package com.example.rag.embedding;

import com.example.rag.config.RagProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 调用 OpenAI 兼容格式的 /embeddings 接口（硅基流动、OpenAI、智谱、本地 vLLM 等都是这个格式）。
 *
 * <p>请求体：
 * <pre>{"model":"BAAI/bge-m3","input":["文本1","文本2"]}</pre>
 * 响应体：
 * <pre>{"data":[{"embedding":[0.01,-0.02,...],"index":0}, ...]}</pre>
 *
 * <p>要点：服务端返回的 data 数组<b>不保证</b>和输入顺序一致，必须按 {@code index} 字段重新排序，
 * 否则向量会错配到别的文本上 —— 这是接这类接口最容易踩的坑。
 */
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);

    private final RestClient restClient;
    private final RagProperties.Embedding cfg;

    public OpenAiCompatibleEmbeddingClient(RagProperties.Embedding cfg) {
        this.cfg = cfg;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(10, cfg.getTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(cfg.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + cfg.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(texts.size());
        int batch = Math.max(1, cfg.getBatchSize());

        // 分批送，避免一次请求体过大被服务端拒绝
        for (int start = 0; start < texts.size(); start += batch) {
            int end = Math.min(start + batch, texts.size());
            result.addAll(requestBatch(texts.subList(start, end)));
        }
        return result;
    }

    private List<float[]> requestBatch(List<String> batch) {
        Map<String, Object> body = Map.of(
                "model", cfg.getModel(),
                "input", batch);

        EmbeddingResponse resp;
        try {
            resp = restClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException e) {
            // 把服务端返回的错误正文带出来，否则只能看到 401/400 这种没信息量的状态码
            String detail = e.getResponseBodyAsString();
            throw new EmbeddingException(
                    "Embedding 接口返回 " + e.getStatusCode().value() + "：" + detail, e);
        } catch (Exception e) {
            throw new EmbeddingException("调用 Embedding 接口失败：" + e.getMessage(), e);
        }

        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            throw new EmbeddingException("Embedding 接口返回了空结果，请检查模型名是否正确：" + cfg.getModel());
        }

        // 按 index 归位，防止服务端乱序返回
        float[][] ordered = new float[batch.size()][];
        for (EmbeddingItem item : resp.data()) {
            int idx = item.index() == null ? -1 : item.index();
            if (idx < 0 || idx >= batch.size()) {
                throw new EmbeddingException("Embedding 接口返回了非法的 index：" + idx);
            }
            ordered[idx] = item.embedding();
        }

        List<float[]> out = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            float[] v = ordered[i];
            if (v == null) {
                throw new EmbeddingException("Embedding 接口缺少 index=" + i + " 的结果");
            }
            if (v.length != cfg.getDimension()) {
                log.warn("返回维度 {} 与配置 rag.embedding.dimension={} 不一致，以实际返回为准",
                        v.length, cfg.getDimension());
            }
            out.add(VectorUtils.l2Normalize(v));
        }
        return out;
    }

    @Override
    public int dimension() {
        return cfg.getDimension();
    }

    @Override
    public String providerName() {
        return "openai-compatible:" + cfg.getModel();
    }

    /** 对应响应体里的 data 数组。 */
    record EmbeddingResponse(List<EmbeddingItem> data) {
    }

    /** 单个向量结果。 */
    record EmbeddingItem(float[] embedding, Integer index) {
    }
}
