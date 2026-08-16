package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.terms.TermHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermHistoryRepository extends JpaRepository<TermHistory, Long> {

    List<TermHistory> findAllByTermIdOrderByCreatedAtDesc(Long termId);
}
