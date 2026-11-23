package com.example.rag.ingest;

/**
 * 文档解析失败（格式不支持、文件损坏、网页抓取失败等）时抛出。
 */
public class IngestException extends RuntimeException {

    public IngestException(String message) {
        super(message);
    }

    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
