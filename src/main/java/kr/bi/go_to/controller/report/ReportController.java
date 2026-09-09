package kr.bi.go_to.controller.report;

import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.report.request.CreateReportRequest;
import kr.bi.go_to.controller.report.response.ReportResponse;
import kr.bi.go_to.service.report.ReportService;
import kr.bi.go_to.spec.ReportApiSpec;
import kr.bi.go_to.usecase.CreateReportUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController implements ReportApiSpec {

    private final CreateReportUseCase createReportUseCase;
    private final ReportService reportService;

    public ReportController(CreateReportUseCase createReportUseCase, ReportService reportService) {
        this.createReportUseCase = createReportUseCase;
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ReportResponse create(
            @AuthenticationPrincipal AuthenticatedMember member, @Valid @RequestBody CreateReportRequest request) {
        return createReportUseCase.execute(member.id(), request);
    }

    @GetMapping("/{id}")
    @Override
    public ReportResponse get(@PathVariable Long id) {
        return ReportResponse.from(reportService.get(id));
    }
}
