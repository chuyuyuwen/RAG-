package com.example.rag.repository;

import com.example.rag.domain.Chunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunk 的数据访问接口。
 */
public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    /** 某份文档下的所有块，按序号排。 */
    List<Chunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    /** 删除某份文档的所有块（删文档时先删块，再删文档）。 */
    @Transactional
    void deleteByDocumentId(Long documentId);

    /** 统计某份文档的块数。 */
    long countByDocumentId(Long documentId);

    /**
     * 把整张表的向量都捞出来，供内存里做余弦相似度计算。
     *
     * <p>为什么写原生 SQL 只查两列？因为如果 {@code findAll()} 会把 content（大文本）
     * 和整个实体都查出来，数据量大时内存压力大。这里只取 id + embedding，
     * 先算相似度，再按 id 回查前 K 条的正文。
     *
     * <p>当然，全表扫描只适合「几千块以内」的教学项目。真上规模必须用向量数据库
     * （pgvector / Milvus / Qdrant），让数据库自己建索引做近似最近邻搜索。
     */
    @Query(value = "select id, embedding from chunk", nativeQuery = true)
    List<Object[]> findAllIdAndEmbedding();

    /** 按 id 列表批量取块，一次查询拿到正文，避免 N+1。 */
    @Query("select c from Chunk c join fetch c.document where c.id in :ids")
    List<Chunk> findAllWithDocumentByIdIn(@Param("ids") List<Long> ids);
}
