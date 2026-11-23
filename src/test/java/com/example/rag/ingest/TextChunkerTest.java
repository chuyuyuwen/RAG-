package com.example.rag.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.rag.config.RagProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 切块器的单元测试。
 *
 * <p>切块直接决定检索质量，所以这里把各种边界情况都覆盖一遍。
 */
class TextChunkerTest {

    /** 造一个指定 size/overlap 的切块器。 */
    private TextChunker chunker(int size, int overlap) {
        RagProperties props = new RagProperties();
        props.getChunk().setSize(size);
        props.getChunk().setOverlap(overlap);
        return new TextChunker(props);
    }

    @Test
    @DisplayName("短文本只切成一块")
    void shortTextGivesSingleChunk() {
        List<String> chunks = chunker(500, 100).split("这是一段很短的话。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("这是一段很短的话");
    }

    @Test
    @DisplayName("长文本被切成多块，每块不超过 size 太多")
    void longTextGivesMultipleChunks() {
        String text = "这是第一句话。".repeat(200);   // 1400 字
        List<String> chunks = chunker(200, 40).split(text);

        assertThat(chunks).hasSizeGreaterThan(3);
        // 允许一点超出（overlap 的尾巴），但不该出现翻倍的情况
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(300));
    }

    @Test
    @DisplayName("相邻块之间有重叠内容，跨块语义不断裂")
    void adjacentChunksOverlap() {
        String text = "AAAAAAAAAA".repeat(30);   // 300 个 A，无标点，会被硬切
        List<String> chunks = chunker(100, 20).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        // 第一块末尾的内容应该出现在第二块开头
        String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 10);
        assertThat(chunks.get(1)).contains(tailOfFirst);
    }

    @Test
    @DisplayName("空文本返回空列表")
    void blankTextGivesEmptyList() {
        assertThat(chunker(500, 100).split("")).isEmpty();
        assertThat(chunker(500, 100).split("   \n\n  ")).isEmpty();
        assertThat(chunker(500, 100).split(null)).isEmpty();
    }

    @Test
    @DisplayName("连续空行被压缩，不产生空白块")
    void doesNotProduceBlankChunks() {
        String text = "第一段。\n\n\n\n\n第二段。\n\n\n第三段。";
        List<String> chunks = chunker(500, 50).split(text);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c -> assertThat(c.isBlank()).isFalse());
    }

    @Test
    @DisplayName("超长无标点串被硬切，不会切成一块超大内容")
    void hardSplitsVeryLongSentence() {
        String text = "啊".repeat(1000);   // 一个句子里 1000 个字
        List<String> chunks = chunker(100, 20).split(text);
        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(120));
    }

    @Test
    @DisplayName("overlap 大于等于 size 时不会死循环")
    void overlapNotSmallerThanSizeDoesNotHang() {
        // 配置错误时也必须能返回，不能卡死
        List<String> chunks = chunker(100, 100).split("测试内容。".repeat(100));
        assertThat(chunks).isNotEmpty();
    }

    @Test
    @DisplayName("块数乘长度大致覆盖原文，没有丢内容")
    void doesNotLoseContent() {
        String text = "Spring Boot 的自动配置通过条件注解实现。";
        List<String> chunks = chunker(20, 5).split(text);
        String joined = String.join("", chunks);
        // 原文里的关键词应该都能在切块结果里找到
        assertThat(joined).contains("自动配置");
        assertThat(joined).contains("条件注解");
    }
}
