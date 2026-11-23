package com.example.rag.chat;

import com.example.rag.llm.LlmClient.Message;
import com.example.rag.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 构造发给大模型的提示词（prompt）。
 *
 * <p>这是 RAG 里 <b>G</b>（Generation）的关键：模型本身不知道你的资料，
 * 必须把检索到的片段<b>塞进 prompt</b>里，让它「读着资料回答」。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>system 消息</b>定角色和规矩：只依据资料回答、不知道就说不知道、不要编；</li>
 *   <li><b>user 消息</b>里放「参考资料 + 问题」，用清晰的编号和分隔符隔开；</li>
 *   <li>要求模型标注引用编号，方便前端把答案和来源对应起来。</li>
 * </ul>
 */
@Component
public class PromptBuilder {

    /** 系统提示词：给模型定规矩。这段话的质量直接决定回答是否老实。 */
    private static final String SYSTEM_PROMPT = """
            你是一个严谨的知识库问答助手。你的回答必须遵守以下规则：

            1. 只能依据【参考资料】中的内容回答，不要使用你自己的先验知识补充。
            2. 如果参考资料里没有能回答问题的信息，就明确说「根据现有资料无法回答这个问题」，
               并说明缺少什么信息。绝对不要编造。
            3. 回答中引用了哪一段资料，就在那句话后面用方括号标出编号，例如 [1]、[2]。
            4. 用中文回答，条理清晰。如果资料里是英文，翻译成中文再回答。
            5. 不要提及「参考资料」「上下文」这类词，直接自然地回答问题。
            """;

    /**
     * 构造消息列表。
     *
     * @param question 用户问题
     * @param chunks   检索到的片段（已按相似度降序）
     * @return 可直接发给大模型的消息列表
     */
    public List<Message> build(String question, List<RetrievedChunk> chunks) {
        return List.of(
                Message.system(SYSTEM_PROMPT),
                Message.user(buildUserPrompt(question, chunks)));
    }

    /**
     * 拼出 user 消息：参考资料 + 问题。
     *
     * <p>格式设计：每段资料用 {@code [编号] 来源：《标题》} 开头，
     * 这样模型很容易照着编号引用，也方便人类核对。
     */
    String buildUserPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        sb.append("【参考资料】\n");
        if (chunks == null || chunks.isEmpty()) {
            sb.append("（没有检索到任何相关资料）\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk c = chunks.get(i);
                sb.append('[').append(i + 1).append("] 来源：")
                        .append(c.documentTitle())
                        .append("（第 ").append(c.chunkIndex() + 1).append(" 段）\n");
                sb.append(c.content().strip()).append("\n\n");
            }
        }

        sb.append("【问题】\n").append(question.strip()).append('\n');
        return sb.toString();
    }
}
