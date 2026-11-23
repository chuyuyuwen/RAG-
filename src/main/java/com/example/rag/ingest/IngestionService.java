package com.example.rag.ingest;

import com.example.rag.domain.Chunk;
import com.example.rag.domain.Document;
import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.embedding.VectorUtils;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.DocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档入库的总调度：<b>抽取文本 → 切块 → 向量化 → 存库</b>。
 *
 * <p>这四步就是 RAG 里常说的 <b>Indexing（建索引）</b>。整个过程只在「新增资料」时跑一次，
 * 之后每次提问只做检索，所以即使慢一点也可以接受。
 *
 * <p>关键设计：<b>先删后存</b>。同一份文档重新入库时，旧块必须清掉，
 * 否则同一段内容会以不同 id 存在两份，检索结果重复。
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TextExtractor extractor;
    private final TextChunker chunker;
    private final WebFetcher webFetcher;
    private final EmbeddingClient embeddingClient;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;

    public IngestionService(TextExtractor extractor,
                            TextChunker chunker,
                            WebFetcher webFetcher,
                            EmbeddingClient embeddingClient,
                            DocumentRepository documentRepository,
                            ChunkRepository chunkRepository) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.webFetcher = webFetcher;
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    /** 入库结果。 */
    public record IngestResult(Long documentId, String title, int charCount, int chunkCount) {
    }

    /**
     * 上传文件入库。
     *
     * @param file 上传的文件
     */
    @Transactional
    public IngestResult ingestFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IngestException("上传的文件是空的。");
        }
        String fileName = file.getOriginalFilename();
        if (!TextExtractor.isSupported(fileName)) {
            throw new IngestException("不支持的文件类型：" + TextExtractor.extensionOf(fileName)
                    + "。目前支持 PDF、DOCX、TXT、Markdown、CSV、JSON。");
        }

        String text;
        try (InputStream in = file.getInputStream()) {
            text = extractor.extract(fileName, in);
        } catch (IOException e) {
            throw new IngestException("读取上传文件失败：" + e.getMessage(), e);
        }

        String title = fileName == null || fileName.isBlank() ? "未命名文件" : fileName;
        return persist(title, "FILE", fileName, text);
    }

    /**
     * 网页链接入库。
     *
     * @param url http/https 地址
     */
    @Transactional
    public IngestResult ingestUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IngestException("网址不能为空。");
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IngestException("网址必须以 http:// 或 https:// 开头。");
        }

        WebFetcher.Page page = webFetcher.fetch(trimmed);
        return persist(page.title(), "URL", trimmed, page.text());
    }

    /**
     * 直接粘贴的文本入库。
     *
     * @param title 用户给的标题
     * @param text  正文
     */
    @Transactional
    public IngestResult ingestText(String title, String text) {
        if (text == null || text.isBlank()) {
            throw new IngestException("文本内容不能为空。");
        }
        String t = title == null || title.isBlank() ? "粘贴的文本" : title.trim();
        return persist(t, "TEXT", null, text);
    }

    /**
     * 真正的落库逻辑，三种来源共用。
     *
     * <p>注意 {@code @Transactional}：整段在一个事务里，中途向量化失败会整体回滚，
     * 不会留下「有 Document 但没 Chunk」的半成品。
     */
    private IngestResult persist(String title, String sourceType, String sourceRef, String text) {
        // 1) 切块
        List<String> pieces = chunker.split(text);
        if (pieces.isEmpty()) {
            throw new IngestException("文本内容为空，切不出任何片段。");
        }

        // 2) 批量向量化（一次网络请求处理多条，比逐条快很多）
        List<float[]> vectors = embeddingClient.embedAll(pieces);
        if (vectors.size() != pieces.size()) {
            throw new IngestException("向量化结果数量与文本块数量不一致："
                    + vectors.size() + " vs " + pieces.size());
        }

        // 3) 存文档元信息
        Document doc = documentRepository.save(
                new Document(title, sourceType, sourceRef, text.length(), pieces.size()));

        // 4) 存块
        List<Chunk> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            float[] vec = vectors.get(i);
            chunks.add(new Chunk(doc, i, pieces.get(i), VectorUtils.toString(vec), vec.length));
        }
        chunkRepository.saveAll(chunks);

        log.info("入库完成：title={}，来源={}，字符数={}，块数={}，向量维度={}",
                title, sourceType, text.length(), pieces.size(), vectors.get(0).length);

        return new IngestResult(doc.getId(), doc.getTitle(), text.length(), pieces.size());
    }

    /** 删除一份文档及其所有块。 */
    @Transactional
    public void deleteDocument(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new IngestException("文档不存在：id=" + documentId);
        }
        // 先删子表再删父表，避免外键约束报错
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
        log.info("已删除文档 id={}", documentId);
    }
}
