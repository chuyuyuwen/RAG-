package com.example.rag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.rag.ingest.IngestionService;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 问答链路的集成测试。
 *
 * <p>测试环境里没有配 DeepSeek 的 Key，所以注入的其实是「未配置」的占位客户端。
 * 这正好覆盖了一个重要场景：<b>没 Key 时不能让接口 500，要返回可读的中文提示</b>，
 * 同时把已经检索到的资料照常返回给前端。
 */
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private IngestionService ingestionService;

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
    @DisplayName("没配 Key 时：不抛异常，回答里是中文提示，但引用来源照常返回")
    void withoutApiKeyReturnsFriendlyMessageAndSources() {
        ingestionService.ingestText("数据库笔记",
                "MySQL 的 InnoDB 引擎使用 B+ 树索引，主键查询效率很高。");

        ChatResponse resp = chatService.ask("InnoDB 用什么索引？", 3);

        assertThat(resp.usedLlm()).isFalse();
        assertThat(resp.answer()).contains("无法生成回答");
        assertThat(resp.answer()).contains("rag.llm.api-key");
        assertThat(resp.sources()).isNotEmpty();
        assertThat(resp.sources().get(0).documentTitle()).isEqualTo("数据库笔记");
        assertThat(resp.sources().get(0).content()).contains("B+ 树");
        assertThat(resp.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("引用来源的编号从 1 开始，且分数按降序排列")
    void sourcesAreNumberedAndSortedByScore() {
        ingestionService.ingestText("数据库笔记",
                "MySQL 的 InnoDB 引擎使用 B+ 树索引。事务隔离级别有四种。");
        ingestionService.ingestText("操作系统笔记",
                "进程是资源分配的基本单位。线程是调度的基本单位。");

        ChatResponse resp = chatService.ask("InnoDB B+ 树索引", 5);

        assertThat(resp.sources()).isNotEmpty();
        for (int i = 0; i < resp.sources().size(); i++) {
            assertThat(resp.sources().get(i).index()).isEqualTo(i + 1);
        }
        double prev = Double.MAX_VALUE;
        for (ChatResponse.Source s : resp.sources()) {
            assertThat(s.score()).isLessThanOrEqualTo(prev);
            prev = s.score();
        }
    }

    @Test
    @DisplayName("知识库为空时：不调模型，直接给出「没找到资料」的中文提示")
    void emptyKnowledgeBaseReturnsExplanationWithoutCallingLlm() {
        ChatResponse resp = chatService.ask("任意问题都可以", 3);

        assertThat(resp.sources()).isEmpty();
        assertThat(resp.usedLlm()).isFalse();
        assertThat(resp.answer()).contains("没有找到与这个问题相关的内容");
        assertThat(resp.answer()).contains("还没有上传相关资料");
    }

    @Test
    @DisplayName("问题为空或全是空白时抛 IllegalArgumentException")
    void blankQuestionIsRejected() {
        assertThatThrownBy(() -> chatService.ask("   ", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("问题不能为空");
        assertThatThrownBy(() -> chatService.ask(null, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("topK 传 null 时用配置里的默认值，不会报错")
    void nullTopKUsesConfiguredDefault() {
        ingestionService.ingestText("数据库笔记", "InnoDB 使用 B+ 树索引，查询很快。");

        ChatResponse resp = chatService.ask("InnoDB 索引", null);

        assertThat(resp.sources()).isNotEmpty();
        assertThat(resp.sources().size()).isLessThanOrEqualTo(5);
    }
}
