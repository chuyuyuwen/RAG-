package com.example.rag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rag.llm.LlmClient.Message;
import com.example.rag.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 提示词构造的单元测试。
 *
 * <p>这里不启动 Spring，只验证「检索片段 → 拼出来的字符串」对不对。
 * prompt 拼错了模型就会答歪，所以值得单独测。
 */
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    private static RetrievedChunk chunk(int index, String title, int chunkIndex, String content) {
        return new RetrievedChunk(1L + index, 10L, title, chunkIndex, content, 0.8);
    }

    @Test
    @DisplayName("生成的 messages 是 system + user 两条，角色正确")
    void buildReturnsSystemAndUserMessages() {
        List<Message> messages = promptBuilder.build("什么是 RAG？",
                List.of(chunk(0, "RAG 原理", 0, "RAG 是检索增强生成。")));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).contains("只能依据【参考资料】");
    }

    @Test
    @DisplayName("参考资料按 [1][2] 编号，并带上文档标题和段号")
    void userPromptNumbersChunksWithTitleAndIndex() {
        String prompt = promptBuilder.buildUserPrompt("问题", List.of(
                chunk(0, "数据库笔记", 0, "InnoDB 用 B+ 树。"),
                chunk(1, "操作系统笔记", 4, "进程是资源分配单位。")));

        assertThat(prompt).contains("[1] 来源：数据库笔记（第 1 段）");
        assertThat(prompt).contains("[2] 来源：操作系统笔记（第 5 段）");
        assertThat(prompt).contains("InnoDB 用 B+ 树。");
        assertThat(prompt).contains("【问题】");
        assertThat(prompt).contains("问题");
    }

    @Test
    @DisplayName("没有检索到片段时，prompt 里明确写没有资料，而不是留空")
    void userPromptHandlesEmptyChunks() {
        String prompt = promptBuilder.buildUserPrompt("问题", List.of());

        assertThat(prompt).contains("（没有检索到任何相关资料）");
        assertThat(prompt).contains("【问题】");
    }

    @Test
    @DisplayName("chunks 为 null 也不炸")
    void userPromptHandlesNullChunks() {
        String prompt = promptBuilder.buildUserPrompt("问题", null);

        assertThat(prompt).contains("（没有检索到任何相关资料）");
    }

    @Test
    @DisplayName("问题首尾空白会被去掉")
    void questionIsTrimmed() {
        String prompt = promptBuilder.buildUserPrompt("  什么是向量检索？  \n", List.of());

        assertThat(prompt).endsWith("什么是向量检索？\n");
    }
}
