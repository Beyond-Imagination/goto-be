package kr.bi.go_to.service.report.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.service.report.ReportService;
import kr.bi.go_to.service.report.model.FacilityNodeData;
import kr.bi.go_to.service.report.model.ReportData;

public class MockReportService implements ReportService {

    private static final List<FacilityNodeData> NODES = List.of(
            new FacilityNodeData(
                    1L, "ELEVATOR", "Main lobby elevator", 37.523850, 126.980470, 1, true, 5, 1L, "Mock Place"),
            new FacilityNodeData(
                    2L, "TOILET", "Accessible toilet", 37.523920, 126.980520, 1, true, 4, 1L, "Mock Place"),
            new FacilityNodeData(
                    3L, "RAMP", "Side entrance ramp", 37.524010, 126.980610, 0, false, null, 1L, "Mock Place"));

    private final AtomicLong reportIdSequence = new AtomicLong(1);
    private final Map<Long, ReportData> createdReports = new ConcurrentHashMap<>();
    private final Clock clock;

    public MockReportService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ReportData create(Long reporterId, Long nodeId, String issueType, String description) {
        FacilityNodeData node = NODES.stream()
                .filter(candidate -> candidate.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FACILITY_NODE_NOT_FOUND));
        ReportData created = new ReportData(
                reportIdSequence.getAndIncrement(),
                reporterId,
                node,
                issueType.trim(),
                trimToNull(description),
                Instant.now(clock));

        createdReports.put(created.id(), created);
        return created;
    }

    @Override
    public ReportData get(Long reportId) {
        ReportData found = createdReports.get(reportId);

        if (found == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        return found;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
