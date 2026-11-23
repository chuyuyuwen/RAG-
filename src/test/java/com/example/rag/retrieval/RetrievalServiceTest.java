package com.example.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rag.domain.Chunk;
import com.example.rag.domain.Document;
import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.embedding.VectorUtils;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.DocumentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 检索服务的集成测试：真的走一遍「存向量 → 查向量 → 算相似度 → 取正文」。
 *
 * <p>用测试配置里的内存 H2 和 fake embedding，所以不联网、可重复跑。
 */
@SpringBootTest
class RetrievalServiceTest {

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private EmbeddingClient embeddingClient;

    @BeforeEach
    void cleanUp() {
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
    }

    /** 造一份文档并入库若干块（向量由当前 embedding 实现现算）。 */
    private Document ingest(String title, String... chunkTexts) {
        Document doc = documentRepository.save(
                new Document(title, "TEXT", null,
                        String.join("", chunkTexts).length(), chunkTexts.length));
        for (int i = 0; i < chunkTexts.length; i++) {
            float[] vec = embeddingClient.embed(chunkTexts[i]);
            chunkRepository.save(new Chunk(doc, i, chunkTexts[i],
                    VectorUtils.toString(vec), vec.length));
        }
        return doc;
    }

    @Test
    @DisplayName("能检索到与问题字面最接近的片段，且排在第一位")
    void retrievesMostRelevantChunkFirst() {
        ingest("数据库笔记",
                "MySQL 的索引使用 B+ 树结构，可以有效减少磁盘随机读取次数。",
                "今天天气不错，适合出去散步和拍照。",
                "Spring Boot 通过自动配置减少样板代码，起步依赖把常用库打包在一起。");

        List<RetrievedChunk> hits = retrievalService.retrieve("MySQL 索引为什么用 B+ 树", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).content()).contains("B+ 树");
        assertThat(hits.get(0).documentTitle()).isEqualTo("数据库笔记");
    }

    @Test
    @DisplayName("结果按相似度从高到低排序")
    void resultsAreSortedByScoreDescending() {
        ingest("混合资料",
                "向量检索通过余弦相似度衡量两段文本的语义接近程度。",
                "余弦相似度计算两个向量夹角的余弦值。",
                "红烧肉要先焯水再炒糖色。");

        List<RetrievedChunk> hits = retrievalService.retrieve("余弦相似度怎么算", 3);

        assertThat(hits).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 1; i < hits.size(); i++) {
            assertThat(hits.get(i - 1).score()).isGreaterThanOrEqualTo(hits.get(i).score());
        }
    }

    @Test
    @DisplayName("topK 参数限制返回条数")
    void topKLimitsResultSize() {
        ingest("多块文档",
                "第一段讲 Java 的垃圾回收机制。",
                "第二段讲 Java 的线程池参数配置。",
                "第三段讲 Java 的集合框架设计。",
                "第四段讲 Java 的反射与动态代理。");

        assertThat(retrievalService.retrieve("Java", 2)).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("资料库为空时返回空列表，而不是抛异常")
    void emptyLibraryReturnsEmptyList() {
        assertThat(retrievalService.retrieve("任意问题", 5)).isEmpty();
    }

    @Test
    @DisplayName("空问题直接返回空列表")
    void blankQuestionReturnsEmpty() {
        ingest("随便一份", "一些内容");
        assertThat(retrievalService.retrieve("   ", 5)).isEmpty();
        assertThat(retrievalService.retrieve(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("维度对不上的脏数据被跳过，不会拖垮整次检索")
    void mismatchedDimensionIsSkipped() {
        Document doc = documentRepository.save(new Document("坏数据", "TEXT", null, 10, 2));
        // 一块维度正确
        float[] good = embeddingClient.embed("正常的块内容");
        chunkRepository.save(new Chunk(doc, 0, "正常的块内容",
                VectorUtils.toString(good), good.length));
        // 一块维度错误（模拟换过 embedding 模型后残留的旧向量）
        chunkRepository.save(new Chunk(doc, 1, "维度不对的块内容",
                VectorUtils.toString(new float[] {1f, 2f, 3f}), 3));

        List<RetrievedChunk> hits = retrievalService.retrieve("正常的块内容", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(h -> assertThat(h.content()).isNotEqualTo("维度不对的块内容"));
    }
}
