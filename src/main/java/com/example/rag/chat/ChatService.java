package com.example.rag.chat;

import com.example.rag.llm.LlmClient;
import com.example.rag.llm.LlmException;
import com.example.rag.retrieval.RetrievalService;
import com.example.rag.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RAG 问答的总调度：<b>检索 → 拼 prompt → 调大模型 → 返回答案 + 来源</b>。
 *
 * <p>这就是整个项目的主干。对比一下传统做法：
 * <ul>
 *   <li>直接问大模型：模型只能靠训练时的记忆，问它你公司的内部文档它一无所知；</li>
 *   <li>RAG：先在你的资料里找到相关段落，再让模型「看着资料回答」，
 *       既能答对私有知识，又能给出出处，还能减少胡编。</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;

    public ChatService(RetrievalService retrievalService,
                       PromptBuilder promptBuilder,
                       LlmClient llmClient) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
    }

    /**
     * 回答问题。
     *
     * @param question 用户问题
     * @param topK     检索几个片段，null 用配置默认值
     * @return 回答 + 引用来源
     */
    public ChatResponse ask(String question, Integer topK) {
        long start = System.currentTimeMillis();

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空。");
        }

        // 1) 检索：找出最相关的几段资料
        List<RetrievedChunk> chunks = retrievalService.retrieve(question, topK);
        log.info("问题「{}」检索到 {} 个片段", question, chunks.size());

        // 2) 组装来源信息（不管有没有配 Key，来源都要返回，前端可以展示「找到了什么」）
        List<ChatResponse.Source> sources = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sources.add(new ChatResponse.Source(
                    i + 1,
                    c.documentId(),
                    c.documentTitle(),
                    c.chunkIndex(),
                    c.content(),
                    c.score()));
        }

        // 3) 一个片段都没检索到，就别浪费 token 调模型了，直接给出明确提示
        if (chunks.isEmpty()) {
            return new ChatResponse(
                    "知识库里没有找到与这个问题相关的内容。\n\n"
                            + "可能的原因：\n"
                            + "1. 还没有上传相关资料 —— 可以在「知识库」页面添加文件、网页或文本；\n"
                            + "2. 问题用词和资料差得太远 —— 换一种说法试试；\n"
                            + "3. 相似度阈值（rag.retrieval.min-score）设得偏高，把结果过滤掉了。",
                    List.of(),
                    false,
                    System.currentTimeMillis() - start);
        }

        // 4) 拼 prompt 并调用大模型
        List<LlmClient.Message> messages = promptBuilder.build(question, chunks);
        String answer;
        boolean usedLlm = true;
        try {
            answer = llmClient.chat(messages);
        } catch (LlmException e) {
            // 关键：把「没配 Key」这类可预期错误降级成正常响应，
            // 让前端能显示提示 + 已检索到的资料，而不是弹一个 500 错误。
            log.warn("调用大模型失败：{}", e.getMessage());
            usedLlm = false;
            answer = "⚠️ 无法生成回答：\n\n" + e.getMessage()
                    + "\n\n（下面已经列出了检索到的相关资料，你可以直接查看。）";
        }

        return new ChatResponse(answer, sources, usedLlm, System.currentTimeMillis() - start);
    }
}
