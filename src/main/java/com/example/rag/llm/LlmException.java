package com.example.rag.llm;

/**
 * 调用大模型失败时抛出。
 *
 * <p>单独定义而不是直接用 RuntimeException，是为了让全局异常处理器能把它翻译成
 * 友好的中文提示（比如「还没配置 DeepSeek API Key」），而不是给用户看 500 堆栈。
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
