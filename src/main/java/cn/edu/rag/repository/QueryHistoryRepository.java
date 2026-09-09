package cn.edu.rag.repository;

import cn.edu.rag.domain.QueryHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {
    List<QueryHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
