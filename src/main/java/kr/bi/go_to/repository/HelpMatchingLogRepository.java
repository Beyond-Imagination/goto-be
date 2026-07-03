package kr.bi.go_to.repository;

import java.util.Optional;
import java.util.UUID;

import kr.bi.go_to.model.help.HelpMatchingLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpMatchingLogRepository extends JpaRepository<HelpMatchingLog, Long> {

    Optional<HelpMatchingLog> findByHelpRequestId(UUID helpRequestId);
}
