package kr.bi.go_to.obstaclereport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.obstaclereport.NearbyObstacleSummary;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

class ObstacleReportServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final ObstacleReportRepository obstacleReportRepository = mock(ObstacleReportRepository.class);
    private final ObstacleReportService service = new ObstacleReportService(
            obstacleReportRepository,
            mock(ObstacleReportConfirmationRepository.class),
            mock(MemberService.class),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("severity 축과 STALE 축은 독립이라 하나의 리포트가 두 카운트에 동시에 잡힐 수 있다")
    void countsSeverityAndStaleIndependently() {
        ObstacleReport staleAndCautious = newReport(ObstacleSeverity.CAUTION, NOW.minusSeconds(60L * 60 * 24 * 31));
        when(obstacleReportRepository.findActiveWithinRadius(eq(126.978), eq(37.5665), eq(1_000.0)))
                .thenReturn(List.of(staleAndCautious));

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000);

        assertThat(summary.cautionCount()).isEqualTo(1);
        assertThat(summary.needsConfirmationCount()).isEqualTo(1);
        assertThat(summary.detourRecommendedCount()).isZero();
        assertThat(summary.safeCount()).isZero();
    }

    @Test
    @DisplayName("severity별로 우회권장/주의/안전 카운트를 나눠 집계한다")
    void countsBySeverity() {
        when(obstacleReportRepository.findActiveWithinRadius(eq(126.978), eq(37.5665), eq(1_000.0)))
                .thenReturn(List.of(
                        newReport(ObstacleSeverity.IMPASSABLE, NOW),
                        newReport(ObstacleSeverity.CAUTION, NOW),
                        newReport(ObstacleSeverity.INFO, NOW)));

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000);

        assertThat(summary.detourRecommendedCount()).isEqualTo(1);
        assertThat(summary.cautionCount()).isEqualTo(1);
        assertThat(summary.safeCount()).isEqualTo(1);
        assertThat(summary.needsConfirmationCount()).isZero();
    }

    @Test
    @DisplayName("반경 내 리포트가 없으면 모든 카운트가 0이다")
    void returnsZeroCountsWhenNoReportsNearby() {
        when(obstacleReportRepository.findActiveWithinRadius(eq(126.978), eq(37.5665), eq(1_000.0)))
                .thenReturn(List.of());

        NearbyObstacleSummary summary = service.getNearbySummary(37.5665, 126.978, 1_000);

        assertThat(summary).isEqualTo(new NearbyObstacleSummary(0, 0, 0, 0));
    }

    private ObstacleReport newReport(ObstacleSeverity severity, Instant lastConfirmedAt) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(126.978, 37.5665));
        return ObstacleReport.builder()
                .locationPoint(point)
                .issueType(ObstacleIssueType.SIDEWALK_DAMAGE)
                .severity(severity)
                .affectedMobilityTypes(Set.of(MobilityType.WHEELCHAIR))
                .lastConfirmedAt(lastConfirmedAt)
                .build();
    }
}
