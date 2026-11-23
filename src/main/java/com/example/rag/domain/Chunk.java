package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 文档被切开后的一个「片段」，这是 RAG 检索的最小单位。
 *
 * <p>为什么必须切块？三个原因：
 * <ol>
 *   <li>embedding 模型有输入长度上限（bge-m3 约 8192 token），整本书塞不进去；</li>
 *   <li>整篇文档压成一个向量，语义会被「平均」掉，检索精度很差；</li>
 *   <li>喂给大模型的上下文有 token 预算，只应该给最相关的几段。</li>
 * </ol>
 *
 * <p>所以流程是：文档 → 切成 N 个块 → 每块算一个向量 → 存起来 → 提问时找最像的几块。
 */
@Entity
@Table(name = "chunk", indexes = {
        // 按文档查块是最常见的操作（删文档、看某文档的块）
        @Index(name = "idx_chunk_document", columnList = "document_id")
})
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 属于哪份文档。多对一，多个块指向同一份文档。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** 这块在文档里的序号，从 0 开始。用于排序和展示「第几段」。 */
    @Column(nullable = false)
    private int chunkIndex;

    /** 块的正文。用 CLOB（H2 里是 CHARACTER LARGE OBJECT），避免长文本被 VARCHAR 长度限制卡住。 */
    @Lob
    @Column(nullable = false)
    private String content;

    /**
     * 向量，以逗号分隔的浮点数字符串存放。
     *
     * <p>为什么不用 float[] + @Convert？其实可以，但字符串更直观：打开 H2 控制台就能看到向量长什么样，
     * 学习阶段排查问题方便。生产环境应该换成 pgvector / Milvus 这类专门的向量库。
     *
     * <p>这里刻意<b>不用 {@code @Lob}</b>：@Lob 的 String 会被映射成 JDBC 的 CLOB，
     * 而原生 SQL 查询返回的是 {@code Clob} 代理对象，直接强转 String 会抛
     * {@code ClassCastException: $Proxy cannot be cast to java.lang.String}。
     * 用足够长的 VARCHAR（1024 维约 12KB）既避开这个坑，也方便比较和调试。
     */
    @Column(nullable = false, length = 40000)
    private String embedding;

    /** 向量维度，冗余存一份便于校验（换模型后旧数据维度会对不上）。 */
    @Column(nullable = false)
    private int dimension;

    protected Chunk() {
    }

    public Chunk(Document document, int chunkIndex, String content, String embedding, int dimension) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.dimension = dimension;
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getEmbedding() {
        return embedding;
    }

    public int getDimension() {
        return dimension;
    }
}
