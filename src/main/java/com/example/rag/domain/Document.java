package com.example.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 一份「原始资料」的元信息：一个上传的 PDF、一个网页链接、一段粘贴的文本，都是一条 Document。
 *
 * <p>注意它<b>不存正文全文</b>。正文被切成很多块存进 {@link Chunk}，因为检索是「按块」做的。
 * 这里只留元信息，方便列表展示和溯源（回答时告诉用户「来自哪个文件」）。
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 展示用的标题，例如文件名「操作系统复习提纲.pdf」或网页标题。 */
    @Column(nullable = false, length = 500)
    private String title;

    /** 来源类型：FILE（上传文件）/ URL（网页）/ TEXT（直接粘贴）。 */
    @Column(nullable = false, length = 20)
    private String sourceType;

    /** 来源定位：原始文件名、完整 URL，或 TEXT 类型时为空。 */
    @Column(length = 2000)
    private String sourceRef;

    /** 原始内容的字符数，用于展示和排查切块是否正常。 */
    @Column(nullable = false)
    private int charCount;

    /** 切出来的块数，省得每次都 count 一遍。 */
    @Column(nullable = false)
    private int chunkCount;

    /** 入库时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    protected Document() {
        // JPA 要求的无参构造器
    }

    public Document(String title, String sourceType, String sourceRef, int charCount, int chunkCount) {
        this.title = title;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.charCount = charCount;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
