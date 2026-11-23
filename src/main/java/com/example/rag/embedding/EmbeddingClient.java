package com.example.rag.embedding;

import java.util.List;

/**
 * 把文本转成向量（embedding）的抽象。
 *
 * <p>为什么要抽象成接口？因为「向量化」这一步可以换很多实现：在线 API、本地模型、假实现。
 * 上层代码（文档入库、检索）只依赖这个接口，换实现时不用改一行业务代码 —— 这就是依赖倒置。
 *
 * <p>实现必须是线程安全的，因为会被多个请求并发调用。
 */
public interface EmbeddingClient {

    /**
     * 把单条文本转成向量。
     *
     * @param text 待向量化的文本，不能为 null
     * @return 定长向量，长度等于 {@link #dimension()}
     */
    float[] embed(String text);

    /**
     * 批量向量化。默认实现是逐条调用，真实实现应该覆盖它并发/批量请求以提高吞吐。
     *
     * @param texts 文本列表，不能为 null
     * @return 与输入顺序一一对应的向量列表
     */
    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /** 向量维度。所有实现返回的向量长度必须一致，否则无法比较。 */
    int dimension();

    /** 便于日志和错误提示的实现名，例如 "fake-hash" / "openai:BAAI/bge-m3"。 */
    String providerName();
}
