package com.example.rag.retrieval;

/**
 * 一条检索结果：命中的片段 + 相似度分数 + 它来自哪份文档。
 *
 * <p>用 record 而不是 class：它就是不可变的数据载体，没有行为，record 自动生成
 * 构造器、getter、equals、hashCode、toString，代码最省。
 */
public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        double score) {
}
