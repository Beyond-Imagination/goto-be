package kr.bi.go_to.repository;

import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ObstacleReportConfirmationRepository extends JpaRepository<ObstacleReportConfirmation, Long> {

    boolean existsByObstacleReport_IdAndMember_Id(Long obstacleReportId, Long memberId);
}
