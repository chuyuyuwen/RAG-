package com.example.rag.repository;

import com.example.rag.domain.Document;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Document 的数据访问接口。
 *
 * <p>Spring Data JPA 的魔法：只要声明接口继承 {@link JpaRepository}，框架就会在启动时
 * <b>自动生成实现类</b>，你不需要写一行 SQL 或实现代码。
 *
 * <p>方法名会被解析成查询：{@code findAllByOrderByCreatedAtDesc} 会被翻译成
 * {@code select * from document order by created_at desc}。这叫「方法名查询」。
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** 按创建时间倒序，最新的排前面（列表页用）。 */
    List<Document> findAllByOrderByCreatedAtDesc();
}
