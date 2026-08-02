package kr.bi.go_to.service.obstaclereport;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.bi.go_to.controller.obstaclereport.request.CreateObstacleReportRequest;
import kr.bi.go_to.controller.obstaclereport.request.ObstacleReportClusterRequest;
import kr.bi.go_to.controller.obstaclereport.request.UpdateObstacleReportStatusRequest;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportClusterResponse;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.service.MemberService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObstacleReportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * 줌 경계값(먼/중간/가까운 줌 구분)은 프론트 네이버 지도 줌 레벨 기준이 아직 확정되지 않아 임의로 정한 값이다.
     * 실제 값이 정해지면 교체 필요 (TODO.md REQ-PM-06 2-2 참고).
     */
    private static final int FAR_ZOOM_UPPER_BOUND = 12;

    private static final int CLOSE_ZOOM_THRESHOLD = 16;
    private static final int FAR_ZOOM_MAX_CLUSTERS = 5;
    private static final int MID_ZOOM_MAX_CLUSTERS = 6;

    private final ObstacleReportRepository obstacleReportRepository;
    private final ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;
    private final MemberService memberService;
    private final Clock clock;

    public ObstacleReportService(
            ObstacleReportRepository obstacleReportRepository,
            ObstacleReportConfirmationRepository obstacleReportConfirmationRepository,
            MemberService memberService,
            Clock clock) {
        this.obstacleReportRepository = obstacleReportRepository;
        this.obstacleReportConfirmationRepository = obstacleReportConfirmationRepository;
        this.memberService = memberService;
        this.clock = clock;
    }

    @Transactional
    public ObstacleReportResponse create(Long memberId, CreateObstacleReportRequest request) {
        Member reporter = memberService.getUser(memberId);
        // Coordinate 순서: (경도, 위도)
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(request.lng(), request.lat()));

        ObstacleReport report = ObstacleReport.builder()
                .reporter(reporter)
                .locationPoint(point)
                .issueType(request.issueType())
                .severity(request.severity())
                .affectedMobilityTypes(request.affectedMobilityTypes())
                .photoUrls(request.photoUrls())
                .build();

        return ObstacleReportResponse.from(obstacleReportRepository.save(report), clock.instant());
    }

    @Transactional(readOnly = true)
    public ObstacleReportResponse get(Long reportId) {
        return ObstacleReportResponse.from(getOrThrow(reportId), clock.instant());
    }

    @Transactional
    public ObstacleReportResponse updateStatus(
            Long memberId, Long reportId, UpdateObstacleReportStatusRequest request) {
        ObstacleReport report = getOrThrow(reportId);
        if (report.isResolved()) {
            throw new BusinessException(ErrorCode.OBSTACLE_REPORT_ALREADY_RESOLVED);
        }

        if (request.action() == UpdateObstacleReportStatusRequest.Action.RESOLVED) {
            report.resolve();
        } else {
            confirm(memberId, report);
        }

        return ObstacleReportResponse.from(report, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ObstacleReportClusterResponse> getClusters(ObstacleReportClusterRequest request) {
        String mobilityType =
                request.mobilityType() != null ? request.mobilityType().name() : null;
        List<ObstacleReport> reports = obstacleReportRepository.findWithinBbox(
                request.minLng(), request.minLat(), request.maxLng(), request.maxLat(), mobilityType);

        if (reports.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        int zoom = request.zoom();

        if (zoom >= CLOSE_ZOOM_THRESHOLD) {
            // 가까운 줌: 클러스터링 없이 제보받은 모든 핀을 그대로 반환한다.
            return reports.stream()
                    .map(report -> ObstacleReportClusterResponse.from(List.of(report), now))
                    .toList();
        }

        // 줌이 1 커질 때마다 격자 한 칸이 절반씩 작아진다 (슬리피맵 타일 크기 규칙과 동일한 halving).
        double cellSizeDegrees = 180.0 / Math.pow(2, zoom);

        Map<GridCell, List<ObstacleReport>> grouped =
                reports.stream().collect(Collectors.groupingBy(r -> GridCell.of(r, cellSizeDegrees)));

        List<ObstacleReportClusterResponse> clusters = grouped.values().stream()
                .map(group -> ObstacleReportClusterResponse.from(group, now))
                // 심각도가 높은(우회권장 > 주의 > 안전) 클러스터, 동률이면 리포트 수가 많은 클러스터를 우선한다.
                .sorted(Comparator.comparingInt((ObstacleReportClusterResponse c) ->
                                c.maxSeverity().ordinal())
                        .thenComparing(ObstacleReportClusterResponse::reportCount, Comparator.reverseOrder()))
                .toList();

        int maxClusters = zoom < FAR_ZOOM_UPPER_BOUND ? FAR_ZOOM_MAX_CLUSTERS : MID_ZOOM_MAX_CLUSTERS;
        return clusters.size() > maxClusters ? clusters.subList(0, maxClusters) : clusters;
    }

    private void confirm(Long memberId, ObstacleReport report) {
        boolean alreadyConfirmed =
                obstacleReportConfirmationRepository.existsByObstacleReport_IdAndMember_Id(report.getId(), memberId);
        if (alreadyConfirmed) {
            return;
        }

        Member member = memberService.getUser(memberId);
        obstacleReportConfirmationRepository.save(ObstacleReportConfirmation.builder()
                .obstacleReport(report)
                .member(member)
                .build());
        report.confirm(clock.instant());
    }

    private ObstacleReport getOrThrow(Long reportId) {
        return obstacleReportRepository
                .findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OBSTACLE_REPORT_NOT_FOUND));
    }

    private record GridCell(long latIndex, long lngIndex) {
        static GridCell of(ObstacleReport report, double cellSizeDegrees) {
            Point point = report.getLocationPoint();
            return new GridCell((long) Math.floor(point.getY() / cellSizeDegrees), (long)
                    Math.floor(point.getX() / cellSizeDegrees));
        }
    }
}
