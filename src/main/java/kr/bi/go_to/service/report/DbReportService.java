package kr.bi.go_to.service.report;

import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.report.Report;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.service.report.model.FacilityNodeData;
import kr.bi.go_to.service.report.model.ReportData;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DbReportService implements ReportService {

    private final ReportRepository reportRepository;
    private final FacilityNodeRepository facilityNodeRepository;
    private final MemberRepository memberRepository;

    public DbReportService(
            ReportRepository reportRepository,
            FacilityNodeRepository facilityNodeRepository,
            MemberRepository memberRepository) {
        this.reportRepository = reportRepository;
        this.facilityNodeRepository = facilityNodeRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public ReportData create(Long reporterId, Long nodeId, String issueType, String description) {
        Member reporter = memberRepository
                .findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        FacilityNode node = facilityNodeRepository
                .findById(nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FACILITY_NODE_NOT_FOUND));

        Report report = reportRepository.save(Report.create(node, reporter, issueType.trim(), trimToNull(description)));
        return toData(report);
    }

    private ReportData toData(Report report) {
        FacilityNode node = report.getNode();
        Point point = node.getGeojsonPoint();
        return new ReportData(
                report.getId(),
                report.getReporter().getId(),
                new FacilityNodeData(
                        node.getId(),
                        node.getNodeType(),
                        node.getName(),
                        latitude(point),
                        longitude(point),
                        node.getFloorMap().getFloorLevel(),
                        node.isCheckpoint(),
                        node.getSnapRadius()),
                report.getIssueType(),
                report.getDescription(),
                report.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private double latitude(Point point) {
        return point == null ? 0 : point.getY();
    }

    private double longitude(Point point) {
        return point == null ? 0 : point.getX();
    }
}
