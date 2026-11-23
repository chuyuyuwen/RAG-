package com.example.rag.chat;

import java.util.List;

/**
 * 一次问答的完整结果，返回给前端。
 *
 * @param answer   模型生成的回答正文
 * @param sources  引用的片段列表（文档名、内容、相似度）
 * @param usedLlm  是否真的调用了大模型。false 表示没配 Key，answer 里是提示文字
 * @param elapsedMs 整个请求耗时（毫秒），方便用户感知性能
 */
public record ChatResponse(
        String answer,
        List<Source> sources,
        boolean usedLlm,
        long elapsedMs) {

    /**
     * 一条引用来源。
     *
     * @param index         在本次回答里的编号（从 1 开始），和 prompt 里的 [1][2] 对应
     * @param documentId    文档 id，前端可以拿它做跳转
     * @param documentTitle 文档标题
     * @param chunkIndex    片段在文档里的序号（从 0 开始）
     * @param content       片段正文
     * @param score         相似度分数，0~1 之间
     */
    public record Source(
            int index,
            Long documentId,
            String documentTitle,
            int chunkIndex,
            String content,
            double score) {
    }
}
