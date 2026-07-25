package kr.bi.go_to.service.report;

import kr.bi.go_to.service.report.model.ReportData;

public interface ReportService {

    ReportData create(Long reporterId, Long nodeId, String issueType, String description);
}
