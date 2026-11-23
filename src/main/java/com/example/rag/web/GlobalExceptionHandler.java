package com.example.rag.web;

import com.example.rag.embedding.EmbeddingException;
import com.example.rag.ingest.IngestException;
import com.example.rag.llm.LlmException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：把后端抛出的各种异常翻译成「用户看得懂的中文 + 合适的 HTTP 状态码」。
 *
 * <p>没有它的话，前端只能收到 500 和一大坨 Java 堆栈，既不好看也没法提示用户。
 * 有了它，Controller 里就可以放心地直接抛异常，不用每个方法都写 try-catch。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数校验失败（@NotBlank 等）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("请求参数不合法");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    /** 文档解析/入库失败。 */
    @ExceptionHandler(IngestException.class)
    public ResponseEntity<Map<String, Object>> handleIngest(IngestException e) {
        log.warn("入库失败：{}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 向量化失败（多半是 Key 不对或网络不通）。 */
    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<Map<String, Object>> handleEmbedding(EmbeddingException e) {
        log.error("向量化失败：{}", e.getMessage(), e);
        return body(HttpStatus.BAD_GATEWAY, "向量化服务调用失败：" + e.getMessage());
    }

    /** 参数不合法（比如问题为空）。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 调用大模型失败。ChatService 内部已经降级处理，这里只是兜底。 */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, Object>> handleLlm(LlmException e) {
        log.warn("调用大模型失败：{}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** 上传文件超过配置的大小限制。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "文件太大了，超过配置的上传上限。");
    }

    /** 兜底：其他所有未预料的异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未处理的异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：" + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? "" : message));
    }
}
