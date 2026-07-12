package kr.bi.go_to.usecase;

import kr.bi.go_to.controller.report.request.CreateReportRequest;
import kr.bi.go_to.controller.report.response.ReportResponse;
import kr.bi.go_to.service.report.ReportService;
import org.springframework.stereotype.Component;

@Component
public class CreateReportUseCase {

    private final ReportService reportService;

    public CreateReportUseCase(ReportService reportService) {
        this.reportService = reportService;
    }

    public ReportResponse execute(Long reporterId, CreateReportRequest request) {
        return ReportResponse.from(
                reportService.create(reporterId, request.nodeId(), request.issueType(), request.description()));
    }
}
