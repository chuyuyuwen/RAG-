package com.example.rag.embedding;

/**
 * 向量化过程中出错（网络、鉴权、返回格式异常）时抛出。
 *
 * <p>继承 RuntimeException 而不是 Exception：调用方通常无法就地恢复，
 * 统一交给 Controller 层的异常处理器翻译成友好提示。
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
