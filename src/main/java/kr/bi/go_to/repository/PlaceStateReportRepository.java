package kr.bi.go_to.repository;

import kr.bi.go_to.model.placereport.PlaceStateReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceStateReportRepository
        extends JpaRepository<PlaceStateReport, Long>, PlaceStateReportRepositoryCustom {

    long countByReporter_Id(Long reporterId);
}
