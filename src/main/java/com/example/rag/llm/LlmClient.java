package com.example.rag.llm;

import java.util.List;

/**
 * 大模型（LLM）的抽象。
 *
 * <p>和 {@code EmbeddingClient} 一样的思路：上层只依赖接口，具体是 DeepSeek、OpenAI
 * 还是本地 Ollama，换实现不用改业务代码。
 *
 * <p>这里只做「一次性返回完整答案」（非流式）。流式（一个字一个字吐）需要 SSE，
 * 体验更好但复杂度高，本项目先用非流式，等界面稳定了再考虑升级。
 */
public interface LlmClient {

    /**
     * 对话补全。
     *
     * @param messages 消息列表，按 OpenAI 的格式：system / user / assistant 三种角色
     * @return 模型生成的回答正文
     * @throws LlmException 网络失败、鉴权失败、未配置 Key 等
     */
    String chat(List<Message> messages);

    /** 便于日志和界面展示的实现名。 */
    String providerName();

    /**
     * 一条对话消息。
     *
     * @param role    system / user / assistant
     * @param content 内容
     */
    record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
