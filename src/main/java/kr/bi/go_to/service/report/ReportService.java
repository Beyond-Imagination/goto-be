package kr.bi.go_to.service.report;

import kr.bi.go_to.service.report.model.ReportData;

public interface ReportService {

    ReportData create(Long reporterId, Long nodeId, String issueType, String description);

    /** 시설 제보 상세 조회. 없으면 BusinessException(REPORT_NOT_FOUND). */
    ReportData get(Long reportId);
}
