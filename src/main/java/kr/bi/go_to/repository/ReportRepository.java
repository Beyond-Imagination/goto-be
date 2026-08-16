package kr.bi.go_to.repository;

import kr.bi.go_to.model.report.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {}
