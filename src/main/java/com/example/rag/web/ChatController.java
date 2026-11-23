package com.example.rag.web;

import com.example.rag.chat.ChatResponse;
import com.example.rag.chat.ChatService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答接口。
 *
 * <p>请求：
 * <pre>POST /api/chat
 * {"question":"MySQL 索引为什么用 B+ 树？","topK":5}</pre>
 *
 * <p>响应：回答正文 + 引用片段列表。
 *
 * <p>为什么用 POST 而不是 GET？因为问题是长文本，放在 URL 里既不好看也有长度限制。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 提问。
     *
     * @param req 问题 + 可选的 topK
     */
    @PostMapping
    public ChatResponse ask(@RequestBody ChatRequest req) {
        return chatService.ask(req.question(), req.topK());
    }

    /**
     * 请求体。
     *
     * @param question 问题，不能为空
     * @param topK     检索几个片段；不传则用配置里的 rag.retrieval.top-k
     */
    public record ChatRequest(@NotBlank String question, Integer topK) {
    }
}
