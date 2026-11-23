package com.example.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 假向量实现的单元测试。
 *
 * <p>重点验证它「能区分字面相近与不相近」—— 这是检索流程能跑通的前提。
 */
class HashingEmbeddingClientTest {

    private final HashingEmbeddingClient client = new HashingEmbeddingClient(256);

    @Test
    @DisplayName("同一段文本两次向量化结果完全一致（确定性）")
    void sameTextGivesSameVector() {
        float[] a = client.embed("Spring Boot 是什么");
        float[] b = client.embed("Spring Boot 是什么");
        assertThat(a).containsExactly(b);
    }

    @Test
    @DisplayName("向量维度等于配置维度")
    void dimensionMatches() {
        assertThat(client.embed("任意文本")).hasSize(256);
        assertThat(client.dimension()).isEqualTo(256);
    }

    @Test
    @DisplayName("向量已归一化，自身余弦相似度为 1")
    void vectorIsNormalized() {
        float[] v = client.embed("向量检索的基本原理");
        assertThat(VectorUtils.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("字面相近的文本相似度明显高于无关文本")
    void similarTextScoresHigherThanUnrelated() {
        float[] query = client.embed("Spring Boot 如何配置数据库连接");
        float[] related = client.embed("Spring Boot 配置数据库连接的方法");
        float[] unrelated = client.embed("今天中午吃什么比较好呢");

        double relatedScore = VectorUtils.cosine(query, related);
        double unrelatedScore = VectorUtils.cosine(query, unrelated);

        assertThat(relatedScore).isGreaterThan(unrelatedScore);
    }

    @Test
    @DisplayName("空文本得到全零向量，不抛异常")
    void blankTextGivesZeroVector() {
        assertThat(client.embed("")).containsOnly(0f);
        assertThat(client.embed(null)).containsOnly(0f);
    }

    @Test
    @DisplayName("批量向量化结果与逐条调用一致")
    void batchMatchesSingle() {
        List<String> texts = List.of("第一段文本", "第二段文本");
        List<float[]> batch = client.embedAll(texts);
        assertThat(batch).hasSize(2);
        assertThat(batch.get(0)).containsExactly(client.embed(texts.get(0)));
        assertThat(batch.get(1)).containsExactly(client.embed(texts.get(1)));
    }

    @Test
    @DisplayName("维度必须为正数")
    void rejectsNonPositiveDimension() {
        assertThatThrownBy(() -> new HashingEmbeddingClient(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("中文按 bigram 切分，英文按单词切分并转小写")
    void tokenizerSplitsChineseAndEnglish() {
        List<String> tokens = HashingEmbeddingClient.tokenize("我用Spring做RAG");
        assertThat(tokens).contains("我用", "做", "spring", "rag");
    }
}
