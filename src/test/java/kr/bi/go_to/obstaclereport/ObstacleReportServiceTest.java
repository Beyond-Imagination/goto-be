package kr.bi.go_to.obstaclereport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.bi.go_to.controller.obstaclereport.request.ObstacleReportClusterRequest;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportClusterResponse;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.obstaclereport.NearbyObstacleSummary;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import kr.bi.go_to.service.obstaclereport.geocoding.NaverReverseGeocodingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ObstacleReportServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final ObstacleReportRepository obstacleReportRepository = mock(ObstacleReportRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final NaverReverseGeocodingClient naverReverseGeocodingClient = mock(NaverReverseGeocodingClient.class);
    private final ObstacleReportService service = new ObstacleReportService(
            obstacleReportRepository,
            mock(ObstacleReportConfirmationRepository.class),
            placeRepository,
            naverReverseGeocodingClient,
            mock(MemberService.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            noopTransactionManager());

    /**
     * 이 테스트는 리포지토리 자체를 mock으로 대체하므로 실제 DB 트랜잭션이 필요 없다 —
     * getClusters()가 내부적으로 여는 TransactionTemplate이 콜백을 정상 실행하도록
     * getTransaction()만 더미 TransactionStatus를 반환하게 한다.
     */
    private static PlatformTransactionManager noopTransactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return transactionManager;
    }

    @Test
    @DisplayName("severity 축과 STALE 축은 독립이라 하나의 리포트가 두 카운트에 동시에 잡힐 수 있다")
    void countsSeverityAndStaleIndependently() {
        ObstacleReport staleAndCautious = newReport(ObstacleSeverity.CAUTION, NOW.minusSeconds(60L * 60 * 24 * 31));
        when(obstacleReportRepository.findActiveWithinRadius(
                        eq(126.978), eq(37.5665), eq(1_000.0), eq(false), eq(Set.of()), eq(false), eq(Set.of())))
                .thenReturn(List.of(staleAndCautious));

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000, Set.of(), Set.of());

        assertThat(summary.cautionCount()).isEqualTo(1);
        assertThat(summary.needsConfirmationCount()).isEqualTo(1);
        assertThat(summary.detourRecommendedCount()).isZero();
        assertThat(summary.safeCount()).isZero();
    }

    @Test
    @DisplayName("severity별로 우회권장/주의/안전 카운트를 나눠 집계한다")
    void countsBySeverity() {
        when(obstacleReportRepository.findActiveWithinRadius(
                        eq(126.978), eq(37.5665), eq(1_000.0), eq(false), eq(Set.of()), eq(false), eq(Set.of())))
                .thenReturn(List.of(
                        newReport(ObstacleSeverity.IMPASSABLE, NOW),
                        newReport(ObstacleSeverity.CAUTION, NOW),
                        newReport(ObstacleSeverity.INFO, NOW)));

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000, Set.of(), Set.of());

        assertThat(summary.detourRecommendedCount()).isEqualTo(1);
        assertThat(summary.cautionCount()).isEqualTo(1);
        assertThat(summary.safeCount()).isEqualTo(1);
        assertThat(summary.needsConfirmationCount()).isZero();
    }

    @Test
    @DisplayName("반경 내 리포트가 없으면 모든 카운트가 0이다")
    void returnsZeroCountsWhenNoReportsNearby() {
        when(obstacleReportRepository.findActiveWithinRadius(
                        eq(126.978), eq(37.5665), eq(1_000.0), eq(false), eq(Set.of()), eq(false), eq(Set.of())))
                .thenReturn(List.of());

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000, Set.of(), Set.of());

        assertThat(summary).isEqualTo(new NearbyObstacleSummary(0, 0, 0, 0));
    }

    @Test
    @DisplayName("mobilityTypes/avoid를 문자열 Set으로 변환해 repository에 그대로 전달한다")
    void passesMultiSelectFiltersToRepository() {
        stubBboxQuery(List.of());

        ObstacleReportClusterRequest request = new ObstacleReportClusterRequest(
                37.5,
                126.9,
                37.6,
                127.1,
                10,
                Set.of(MobilityType.WHEELCHAIR, MobilityType.STROLLER),
                Set.of(ObstacleIssueType.HIGH_CURB));

        service.getClusters(request);

        verify(obstacleReportRepository)
                .findWithinBbox(
                        126.9,
                        37.5,
                        127.1,
                        37.6,
                        true,
                        Set.of(MobilityType.WHEELCHAIR.name(), MobilityType.STROLLER.name()),
                        true,
                        Set.of(ObstacleIssueType.HIGH_CURB.name()));
    }

    @Test
    @DisplayName("필터를 지정하지 않으면 hasMobilityTypes/hasAvoid를 false로 전달한다")
    void passesEmptyFiltersAsDisabled() {
        stubBboxQuery(List.of());

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 10, null, null);

        service.getClusters(request);

        verify(obstacleReportRepository).findWithinBbox(126.9, 37.5, 127.1, 37.6, false, Set.of(), false, Set.of());
    }

    @Test
    @DisplayName("가까운 줌(줌 >= CLOSE_ZOOM_THRESHOLD)에서는 제보 1건짜리 클러스터마다 id/photoUrls를 채운다")
    void includesIdAndPhotoUrlsAtCloseZoom() {
        ObstacleReport report = newReportWithId(1L, ObstacleSeverity.CAUTION, List.of("https://example.com/a.jpg"));
        stubBboxQuery(List.of(report));

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 16, null, null);

        List<ObstacleReportClusterResponse> clusters = service.getClusters(request);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).id()).isEqualTo(1L);
        assertThat(clusters.get(0).photoUrls()).containsExactly("https://example.com/a.jpg");
        assertThat(clusters.get(0).nearbyPlaceLabel()).isNull();
        verify(placeRepository, never()).findNearbyActivePlaces(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("먼 줌에서는 nearbyPlaceLabel을 계산하지 않는다(id/photoUrls도 여러 리포트 클러스터라 null)")
    void skipsNearbyPlaceLabelAtFarZoom() {
        stubBboxQuery(List.of(newReport(ObstacleSeverity.CAUTION, NOW), newReport(ObstacleSeverity.CAUTION, NOW)));

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 10, null, null);

        List<ObstacleReportClusterResponse> clusters = service.getClusters(request);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).nearbyPlaceLabel()).isNull();
        assertThat(clusters.get(0).id()).isNull();
        assertThat(clusters.get(0).photoUrls()).isNull();
        verify(placeRepository, never()).findNearbyActivePlaces(anyDouble(), anyDouble(), anyInt(), anyInt());
        verify(naverReverseGeocodingClient, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("중간 줌에서 최근접 장소가 있으면 그 이름으로 nearbyPlaceLabel을 채운다")
    void resolvesNearbyPlaceLabelFromPlaceWhenFound() {
        stubBboxQuery(List.of(newReport(ObstacleSeverity.CAUTION, NOW)));
        when(placeRepository.findNearbyActivePlaces(anyDouble(), anyDouble(), eq(500), eq(1)))
                .thenReturn(List.of(Place.builder().name("국립중앙박물관").build()));

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 13, null, null);

        List<ObstacleReportClusterResponse> clusters = service.getClusters(request);

        assertThat(clusters.get(0).nearbyPlaceLabel()).isEqualTo("국립중앙박물관 인근");
        verify(naverReverseGeocodingClient, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("중간 줌에서 최근접 장소가 없으면 네이버 리버스 지오코딩으로 폴백한다")
    void resolvesNearbyPlaceLabelFromReverseGeocodingWhenPlaceNotFound() {
        stubBboxQuery(List.of(newReport(ObstacleSeverity.CAUTION, NOW)));
        when(placeRepository.findNearbyActivePlaces(anyDouble(), anyDouble(), eq(500), eq(1)))
                .thenReturn(List.of());
        when(naverReverseGeocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Optional.of("종로구 청운동"));

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 13, null, null);

        List<ObstacleReportClusterResponse> clusters = service.getClusters(request);

        assertThat(clusters.get(0).nearbyPlaceLabel()).isEqualTo("종로구 청운동 인근");
    }

    @Test
    @DisplayName("최근접 장소도 리버스 지오코딩도 실패하면 nearbyPlaceLabel은 null이다")
    void nearbyPlaceLabelIsNullWhenBothResolutionsFail() {
        stubBboxQuery(List.of(newReport(ObstacleSeverity.CAUTION, NOW)));
        when(placeRepository.findNearbyActivePlaces(anyDouble(), anyDouble(), eq(500), eq(1)))
                .thenReturn(List.of());
        when(naverReverseGeocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        ObstacleReportClusterRequest request =
                new ObstacleReportClusterRequest(37.5, 126.9, 37.6, 127.1, 13, null, null);

        List<ObstacleReportClusterResponse> clusters = service.getClusters(request);

        assertThat(clusters.get(0).nearbyPlaceLabel()).isNull();
    }

    private void stubBboxQuery(List<ObstacleReport> reports) {
        when(obstacleReportRepository.findWithinBbox(
                        anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(reports);
    }

    private ObstacleReport newReport(ObstacleSeverity severity, Instant lastConfirmedAt) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(126.978, 37.5665));
        ObstacleReport report = ObstacleReport.builder()
                .locationPoint(point)
                .issueType(ObstacleIssueType.SIDEWALK_DAMAGE)
                .severity(severity)
                .affectedMobilityTypes(Set.of(MobilityType.WHEELCHAIR))
                .lastConfirmedAt(lastConfirmedAt)
                .build();
        // BaseAuditEntity.createdAt은 @CreatedDate라 실제 저장 없이는 null이라, from()의 latestReportAt
        // 계산(getCreatedAt() 기반)이 순수 단위 테스트에서 NPE 나지 않도록 직접 채워준다.
        ReflectionTestUtils.setField(report, "createdAt", NOW);
        return report;
    }

    private ObstacleReport newReportWithId(Long id, ObstacleSeverity severity, List<String> photoUrls) {
        ObstacleReport report = newReport(severity, NOW);
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "photoUrls", photoUrls);
        return report;
    }
}
