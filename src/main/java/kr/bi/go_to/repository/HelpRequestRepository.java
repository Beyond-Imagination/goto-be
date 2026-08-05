package kr.bi.go_to.repository;

import java.util.List;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID>, HelpRequestRepositoryCustom {

    List<HelpRequest> findByRequesterIdOrHelperIdOrderByRequestedAtDesc(Long requesterId, Long helperId);
}
