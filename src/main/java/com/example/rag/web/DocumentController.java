package com.example.rag.web;

import com.example.rag.domain.Document;
import com.example.rag.ingest.IngestionService;
import com.example.rag.repository.DocumentRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口：上传文件、抓网页、粘贴文本、列文档、删文档。
 *
 * <p>这就是「往知识库里塞资料」的入口。三个 @PostMapping 分别对应三种来源。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DocumentController(IngestionService ingestionService,
                              DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    /**
     * 上传文件入库。用 multipart/form-data，字段名 file。
     *
     * <p>curl 示例：
     * <pre>curl -F "file=@笔记.pdf" http://localhost:8080/api/documents/upload</pre>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionService.IngestResult upload(@RequestParam("file") MultipartFile file) {
        return ingestionService.ingestFile(file);
    }

    /**
     * 网页链接入库。
     *
     * <p>请求体：{@code {"url":"https://example.com/article"}}
     */
    @PostMapping("/url")
    public IngestionService.IngestResult addUrl(@RequestBody UrlRequest req) {
        return ingestionService.ingestUrl(req.url());
    }

    /**
     * 粘贴文本入库。
     *
     * <p>请求体：{@code {"title":"我的笔记","text":"正文内容……"}}
     */
    @PostMapping("/text")
    public IngestionService.IngestResult addText(@RequestBody TextRequest req) {
        return ingestionService.ingestText(req.title(), req.text());
    }

    /**
     * 列出知识库里所有文档（新的在前）。
     *
     * <p>不返回正文，只返回元信息，避免响应过大。
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    /** 删除一份文档及其所有片段。 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        ingestionService.deleteDocument(id);
        return Map.of("deleted", true, "id", id);
    }

    private Map<String, Object> toView(Document d) {
        return Map.of(
                "id", d.getId(),
                "title", d.getTitle(),
                "sourceType", d.getSourceType(),
                "sourceRef", d.getSourceRef() == null ? "" : d.getSourceRef(),
                "charCount", d.getCharCount(),
                "chunkCount", d.getChunkCount(),
                "createdAt", d.getCreatedAt().toString());
    }

    /** 网页入库请求体。 */
    public record UrlRequest(@NotBlank String url) {
    }

    /** 文本入库请求体。 */
    public record TextRequest(String title, @NotBlank String text) {
    }
}
