package com.example.rag.embedding;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据 {@code rag.embedding.provider} 决定装配哪个 {@link EmbeddingClient} 实现。
 *
 * <p>这是「策略模式 + 工厂」的最小写法：配置写 fake 就用本地哈希实现，写 real 就用在线 API。
 * 好处是<b>没有 API Key 也能把整套流程跑通</b>，等 Key 到手只改一行配置即可切换，业务代码零改动。
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public EmbeddingClient embeddingClient(RagProperties props) {
        RagProperties.Embedding cfg = props.getEmbedding();
        String provider = cfg.getProvider() == null ? "fake" : cfg.getProvider().trim().toLowerCase();

        return switch (provider) {
            case "fake" -> {
                log.info("Embedding 使用本地假实现（哈希词袋，维度 {}）。"
                        + "注意：它只按字面匹配，不理解语义。要真实语义检索请把 "
                        + "rag.embedding.provider 改成 real 并填 API Key。", cfg.getDimension());
                yield new HashingEmbeddingClient(cfg.getDimension());
            }
            case "real" -> {
                if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
                    throw new IllegalStateException(
                            "rag.embedding.provider=real 但没有配置 API Key。"
                                    + "请在 application-local.properties 里设置 rag.embedding.api-key=你的Key，"
                                    + "或把 provider 改回 fake。");
                }
                log.info("Embedding 使用在线接口：{} / {}", cfg.getBaseUrl(), cfg.getModel());
                yield new OpenAiCompatibleEmbeddingClient(cfg);
            }
            default -> throw new IllegalStateException(
                    "未知的 rag.embedding.provider=" + provider + "，只支持 fake 或 real");
        };
    }
}
