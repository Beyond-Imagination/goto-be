package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.terms.UserTermAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermAgreementRepository extends JpaRepository<UserTermAgreement, Long> {

    List<UserTermAgreement> findAllByUserId(Long userId);
}
