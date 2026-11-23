package com.example.rag.retrieval;

import com.example.rag.config.RagProperties;
import com.example.rag.domain.Chunk;
import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.embedding.VectorUtils;
import com.example.rag.repository.ChunkRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量检索：给一个问题，找出资料库里语义最接近的若干片段。
 *
 * <p>这是 RAG 的 <b>R</b>（Retrieval）。整体思路：
 * <ol>
 *   <li>把问题也做一次 embedding，得到「问题向量」；</li>
 *   <li>拿问题向量和库里每个块的向量算余弦相似度；</li>
 *   <li>按分数从高到低排序，取前 K 个，并过滤掉分数过低的。</li>
 * </ol>
 *
 * <p>关键洞察：<b>问题和文档必须用同一个 embedding 模型</b>。因为它们被映射到同一个向量空间里，
 * 换模型就等于换坐标系，两边的向量不再可比。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final ChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final RagProperties props;

    public RetrievalService(ChunkRepository chunkRepository,
                            EmbeddingClient embeddingClient,
                            RagProperties props) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.props = props;
    }

    /**
     * 检索与问题最相关的片段。
     *
     * @param question 用户问题
     * @param topK     取前几个，传 null 用配置里的默认值
     * @return 按相似度降序排列的片段，可能为空（库是空的，或分数都低于阈值）
     */
    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(String question, Integer topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        int k = topK != null && topK > 0 ? topK : props.getRetrieval().getTopK();
        double minScore = effectiveMinScore();

        // 1) 问题向量化
        float[] queryVec = embeddingClient.embed(question);

        // 2) 全量取 (id, embedding)。教学项目够用；生产该换向量库。
        List<Object[]> rows = chunkRepository.findAllIdAndEmbedding();
        if (rows.isEmpty()) {
            return List.of();
        }

        // 3) 算相似度，只保留 id 和分数（正文稍后批量查）
        List<Scored> scored = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            String embStr = (String) row[1];
            float[] vec = VectorUtils.fromString(embStr);

            // 维度不一致说明库里有换模型之前留下的旧数据，跳过而不是抛异常，
            // 否则一条脏数据会让整个检索全挂。
            if (vec.length != queryVec.length) {
                log.warn("块 {} 的向量维度 {} 与当前模型 {} 不一致，已跳过。请重建该文档。",
                        id, vec.length, queryVec.length);
                continue;
            }
            scored.add(new Scored(id, VectorUtils.cosine(queryVec, vec)));
        }

        if (scored.isEmpty()) {
            return List.of();
        }

        // 4) 排序取前 K，并过滤低分
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Long> topIds = scored.stream()
                .limit(Math.max(k * 3L, k))   // 多取一些，过滤后再截断到 k，避免过滤后不够数
                .filter(s -> s.score() >= minScore)
                .limit(k)
                .map(Scored::id)
                .toList();

        if (topIds.isEmpty()) {
            return List.of();
        }

        // 5) 批量取正文（一次查询，避免 N+1）
        Map<Long, Chunk> byId = new LinkedHashMap<>();
        for (Chunk c : chunkRepository.findAllWithDocumentByIdIn(topIds)) {
            byId.put(c.getId(), c);
        }

        // 6) 按分数顺序组装结果
        Map<Long, Double> scoreById = new LinkedHashMap<>();
        for (Scored s : scored) {
            scoreById.put(s.id(), s.score());
        }

        List<RetrievedChunk> result = new ArrayList<>(topIds.size());
        for (Long id : topIds) {
            Chunk c = byId.get(id);
            if (c == null) {
                continue;   // 并发删除的情况，忽略
            }
            result.add(new RetrievedChunk(
                    c.getId(),
                    c.getDocument().getId(),
                    c.getDocument().getTitle(),
                    c.getChunkIndex(),
                    c.getContent(),
                    scoreById.getOrDefault(id, 0.0)));
        }
        return result;
    }

    /** 中间态：只带 id 和分数，轻量。 */
    private record Scored(Long id, double score) {
    }

    /**
     * 实际生效的相似度阈值。
     *
     * <p>为什么需要按实现区分？因为不同 embedding 的「分数尺度」完全不同：
     * <ul>
     *   <li>真实模型（bge-m3）语义相近的两段话，余弦相似度通常在 0.5~0.8；</li>
     *   <li>本地哈希假向量只按字面重合度打分，即使高度相关也常常只有 0.05~0.3。</li>
     * </ul>
     * 用同一个 0.2 阈值，假模式会把几乎所有结果都过滤掉，导致「明明有资料却检索不到」。
     * 所以假模式下把阈值按比例压低，让流程能跑通；换成 real 后自动恢复成配置值。
     */
    private double effectiveMinScore() {
        double configured = props.getRetrieval().getMinScore();
        if (embeddingClient.providerName().startsWith("fake")) {
            return configured * 0.25;
        }
        return configured;
    }
}
