package com.example.rag.llm;

import com.example.rag.config.RagProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配大模型客户端。
 *
 * <p>关键设计：<b>没配 API Key 时应用照样能启动</b>，只是真正提问时会得到一个明确的
 * 中文错误提示。这样用户可以在没有 Key 的情况下先把界面、上传、检索都跑通，
 * 不会因为一个 Key 卡住整个项目。
 *
 * <p>如果这里直接 throw 让启动失败，体验会很差 —— 用户连首页都看不到，
 * 也不知道到底缺什么。
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(RagProperties props) {
        RagProperties.Llm cfg = props.getLlm();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            log.warn("未配置 rag.llm.api-key，问答功能将不可用。"
                    + "请在 application-local.properties 里设置 rag.llm.api-key=你的DeepSeekKey。");
            return new UnconfiguredLlmClient();
        }

        log.info("大模型使用在线接口：{} / {}", cfg.getBaseUrl(), cfg.getModel());
        return new OpenAiCompatibleLlmClient(cfg);
    }

    /**
     * 占位实现：没配 Key 时用它，任何调用都抛出带引导信息的异常。
     *
     * <p>为什么不返回 null？因为那样每个调用点都要判空，代码会到处是 if。
     * 用一个「一调用就抛明确异常」的对象，比 null 安全得多（这是空对象模式）。
     */
    static class UnconfiguredLlmClient implements LlmClient {

        @Override
        public String chat(List<Message> messages) {
            throw new LlmException(
                    "还没有配置 DeepSeek API Key，所以暂时无法生成回答。\n"
                            + "解决办法：在项目根目录新建 application-local.properties，写入：\n"
                            + "  rag.llm.api-key=sk-你的Key\n"
                            + "然后重启应用。Key 可以在 https://platform.deepseek.com 申请。\n"
                            + "（检索功能不受影响，你现在仍然可以上传资料。）");
        }

        @Override
        public String providerName() {
            return "unconfigured";
        }
    }
}
