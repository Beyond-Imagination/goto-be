package kr.bi.go_to.service.obstaclereport;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.bi.go_to.controller.obstaclereport.request.CreateObstacleReportRequest;
import kr.bi.go_to.controller.obstaclereport.request.ObstacleReportClusterRequest;
import kr.bi.go_to.controller.obstaclereport.request.UpdateObstacleReportStatusRequest;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportClusterResponse;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportResponse;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.obstaclereport.geocoding.NaverReverseGeocodingClient;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ObstacleReportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * 줌 경계값(먼/중간/가까운 줌 구분)은 goto-fe `src/screens/home/zoomTiers.ts`의
     * FAR_ZOOM_UPPER_BOUND/CLOSE_ZOOM_THRESHOLD와 반드시 같은 값을 유지해야 한다.
     */
    private static final int FAR_ZOOM_UPPER_BOUND = 12;

    private static final int CLOSE_ZOOM_THRESHOLD = 16;
    private static final int FAR_ZOOM_MAX_CLUSTERS = 5;
    private static final int MID_ZOOM_MAX_CLUSTERS = 6;

    /** 중간 줌 "주변 접근성 이슈" 라벨링 시 최근접 장소를 찾는 반경. */
    private static final int NEARBY_PLACE_LABEL_RADIUS_METERS = 500;

    private final ObstacleReportRepository obstacleReportRepository;
    private final ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;
    private final PlaceRepository placeRepository;
    private final NaverReverseGeocodingClient naverReverseGeocodingClient;
    private final MemberService memberService;
    private final Clock clock;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public ObstacleReportService(
            ObstacleReportRepository obstacleReportRepository,
            ObstacleReportConfirmationRepository obstacleReportConfirmationRepository,
            PlaceRepository placeRepository,
            NaverReverseGeocodingClient naverReverseGeocodingClient,
            MemberService memberService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.obstacleReportRepository = obstacleReportRepository;
        this.obstacleReportConfirmationRepository = obstacleReportConfirmationRepository;
        this.placeRepository = placeRepository;
        this.naverReverseGeocodingClient = naverReverseGeocodingClient;
        this.memberService = memberService;
        this.clock = clock;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
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
    public NearbyObstacleSummary getNearbySummary(
            double lat,
            double lng,
            double radiusMeters,
            Set<MobilityType> mobilityTypeFilter,
            Set<ObstacleIssueType> avoidFilter) {
        Set<String> mobilityTypes = mobilityTypeFilter.stream().map(Enum::name).collect(Collectors.toSet());
        Set<String> avoid = avoidFilter.stream().map(Enum::name).collect(Collectors.toSet());

        List<ObstacleReport> reports = obstacleReportRepository.findActiveWithinRadius(
                lng, lat, radiusMeters, !mobilityTypes.isEmpty(), mobilityTypes, !avoid.isEmpty(), avoid);
        Instant now = clock.instant();

        int detourRecommendedCount = 0;
        int cautionCount = 0;
        int safeCount = 0;
        int needsConfirmationCount = 0;

        for (ObstacleReport report : reports) {
            switch (report.getSeverity()) {
                case IMPASSABLE -> detourRecommendedCount++;
                case CAUTION -> cautionCount++;
                case INFO -> safeCount++;
                default -> throw new IllegalStateException("Unknown ObstacleSeverity: " + report.getSeverity());
            }
            // severity 축과 STALE 축은 독립이라, 하나의 리포트가 두 카운트에 동시에 잡힐 수 있다.
            if (report.isStale(now)) {
                needsConfirmationCount++;
            }
        }

        return new NearbyObstacleSummary(detourRecommendedCount, cautionCount, safeCount, needsConfirmationCount);
    }

    public List<ObstacleReportClusterResponse> getClusters(ObstacleReportClusterRequest request) {
        // DB 조회/클러스터링은 짧은 읽기 전용 트랜잭션 안에서 끝낸다. 중간 줌의 nearbyPlaceLabel
        // 계산(외부 Reverse Geocoding API 호출 포함)은 이 트랜잭션이 끝난 뒤 별도로 수행해,
        // 외부 API가 느려지거나 멈춰도 DB 커넥션을 붙든 채로 대기하지 않도록 한다.
        List<ObstacleReportClusterResponse> capped =
                readOnlyTransactionTemplate.execute(status -> queryCappedClusters(request));

        int zoom = request.zoom();
        if (capped == null || capped.isEmpty() || zoom < FAR_ZOOM_UPPER_BOUND || zoom >= CLOSE_ZOOM_THRESHOLD) {
            return capped == null ? List.of() : capped;
        }

        // 중간 줌: "주변 접근성 이슈" 카드용 최근접 장소/행정동 라벨을 붙인다.
        return capped.stream()
                .map(cluster ->
                        cluster.withNearbyPlaceLabel(resolveNearbyPlaceLabel(cluster.centerLat(), cluster.centerLng())))
                .toList();
    }

    private List<ObstacleReportClusterResponse> queryCappedClusters(ObstacleReportClusterRequest request) {
        Set<String> mobilityTypes =
                request.mobilityTypes().stream().map(Enum::name).collect(Collectors.toSet());
        Set<String> avoid = request.avoid().stream().map(Enum::name).collect(Collectors.toSet());

        List<ObstacleReport> reports = obstacleReportRepository.findWithinBbox(
                request.minLng(),
                request.minLat(),
                request.maxLng(),
                request.maxLat(),
                !mobilityTypes.isEmpty(),
                mobilityTypes,
                !avoid.isEmpty(),
                avoid);

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
        // 먼 줌: 외부 API 호출 비용을 아끼기 위해 nearbyPlaceLabel은 getClusters()에서 이 zoom 대역을
        // 걸러내며, 여기서는 항상 라벨 없는 capped 리스트만 반환한다.
        return clusters.size() > maxClusters ? clusters.subList(0, maxClusters) : clusters;
    }

    /** 최근접 활성 장소를 먼저 찾고, 없으면 네이버 리버스 지오코딩으로 행정동/도로명을 폴백한다. 둘 다 실패하면 null. */
    private String resolveNearbyPlaceLabel(double lat, double lng) {
        List<Place> nearbyPlaces =
                placeRepository.findNearbyActivePlaces(lat, lng, NEARBY_PLACE_LABEL_RADIUS_METERS, 1);
        if (!nearbyPlaces.isEmpty()) {
            return nearbyPlaces.get(0).getName() + " 인근";
        }

        return naverReverseGeocodingClient
                .reverseGeocode(lat, lng)
                .map(administrativeAreaName -> administrativeAreaName + " 인근")
                .orElse(null);
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
