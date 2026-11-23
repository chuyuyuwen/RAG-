package com.example.rag.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rag.domain.Chunk;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.retrieval.RetrievalService;
import com.example.rag.retrieval.RetrievedChunk;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 入库服务的集成测试：真的把文本切块、算向量、写进内存数据库，再检索回来验证。
 *
 * <p>这条测试串起了「入库 → 检索」的完整链路，是第 3 步最重要的验收。
 */
@SpringBootTest
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @BeforeEach
    void cleanUp() {
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("粘贴文本入库：生成文档和多个块，且块数等于返回的 chunkCount")
    void ingestTextCreatesDocumentAndChunks() {
        String text = "Spring Boot 是一个快速开发框架。它通过自动配置减少样板代码。"
                + "起步依赖把常用库打包在一起。内嵌 Tomcat 让应用可以独立运行。";

        IngestionService.IngestResult result = ingestionService.ingestText("Spring 笔记", text);

        assertThat(result.documentId()).isNotNull();
        assertThat(result.title()).isEqualTo("Spring 笔记");
        assertThat(result.charCount()).isEqualTo(text.length());
        assertThat(result.chunkCount()).isGreaterThan(0);

        assertThat(documentRepository.count()).isEqualTo(1);
        assertThat(chunkRepository.countByDocumentId(result.documentId()))
                .isEqualTo(result.chunkCount());
    }

    @Test
    @DisplayName("入库后立刻能被检索到")
    void ingestedTextIsRetrievable() {
        ingestionService.ingestText("数据库笔记",
                "MySQL 的 InnoDB 引擎使用 B+ 树索引，主键查询效率很高。"
                        + "事务的隔离级别有四种：读未提交、读已提交、可重复读、串行化。");

        List<RetrievedChunk> hits = retrievalService.retrieve("InnoDB 索引结构", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).content()).contains("B+ 树");
        assertThat(hits.get(0).documentTitle()).isEqualTo("数据库笔记");
    }

    @Test
    @DisplayName("上传 .md 文件入库，标题用文件名")
    void ingestMarkdownFile() {
        String content = "# 操作系统\n\n进程是资源分配的基本单位。\n\n线程是调度的基本单位。";
        MockMultipartFile file = new MockMultipartFile(
                "file", "操作系统.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));

        IngestionService.IngestResult result = ingestionService.ingestFile(file);

        assertThat(result.title()).isEqualTo("操作系统.md");
        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(chunkRepository.countByDocumentId(result.documentId()))
                .isEqualTo(result.chunkCount());
    }

    @Test
    @DisplayName("上传 .txt 文件入库")
    void ingestTxtFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "一些纯文本笔记内容。".getBytes(StandardCharsets.UTF_8));

        IngestionService.IngestResult result = ingestionService.ingestFile(file);

        assertThat(result.title()).isEqualTo("notes.txt");
        assertThat(documentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("删除文档时，它的块也一起删掉，不留孤儿数据")
    void deleteDocumentAlsoDeletesChunks() {
        IngestionService.IngestResult result = ingestionService.ingestText("待删除", "一段会被删掉的内容。");
        Long docId = result.documentId();
        assertThat(chunkRepository.countByDocumentId(docId)).isGreaterThan(0);

        ingestionService.deleteDocument(docId);

        assertThat(documentRepository.findById(docId)).isEmpty();
        assertThat(chunkRepository.countByDocumentId(docId)).isZero();
    }

    @Test
    @DisplayName("同一内容入库两次会得到两份独立文档，各自的块互不干扰")
    void ingestingSameTextTwiceCreatesTwoDocuments() {
        String text = "重复入库的内容测试。";
        IngestionService.IngestResult first = ingestionService.ingestText("第一份", text);
        IngestionService.IngestResult second = ingestionService.ingestText("第二份", text);

        assertThat(first.documentId()).isNotEqualTo(second.documentId());
        assertThat(documentRepository.count()).isEqualTo(2);

        ingestionService.deleteDocument(first.documentId());

        // 删掉第一份后，第二份必须完好
        assertThat(chunkRepository.countByDocumentId(second.documentId()))
                .isEqualTo(second.chunkCount());
    }

    @Test
    @DisplayName("块内容拼接起来能覆盖原文的关键词")
    void chunksCoverOriginalKeywords() {
        String text = "RAG 的核心思想是检索增强生成。先把资料切片并向量化存起来，"
                + "提问时检索最相关的片段，再把这些片段作为上下文交给大模型生成答案。";
        IngestionService.IngestResult result = ingestionService.ingestText("RAG 原理", text);

        List<Chunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(result.documentId());
        String joined = chunks.stream().map(Chunk::getContent).reduce("", (a, b) -> a + b);

        assertThat(joined).contains("检索增强生成");
        assertThat(joined).contains("向量化");
        assertThat(joined).contains("大模型");
    }
}
