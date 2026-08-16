package kr.bi.go_to.repository;

import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.terms.Term;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findAllByActiveTrueOrderByBitmaskAsc();

    Optional<Term> findByTermKeyAndActiveTrue(String termKey);
}
