package kr.bi.go_to.usecase;

import kr.bi.go_to.controller.place.request.NearbyAccessibilitySummaryRequest;
import kr.bi.go_to.controller.place.response.NearbyAccessibilitySummaryResponse;
import kr.bi.go_to.service.obstaclereport.NearbyObstacleSummary;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import org.springframework.stereotype.Component;

@Component
public class GetNearbyAccessibilitySummaryUseCase {

    /**
     * "내 주변" 반경 — 반경을 사용자가 조절하는 UI가 기획에 없어 서버 고정 상수로 둔다(ADR-0005).
     * 프론트 실측 요구사항이 정해지면 교체 필요.
     */
    private static final double NEARBY_RADIUS_METERS = 1_000;

    private final ObstacleReportService obstacleReportService;

    public GetNearbyAccessibilitySummaryUseCase(ObstacleReportService obstacleReportService) {
        this.obstacleReportService = obstacleReportService;
    }

    public NearbyAccessibilitySummaryResponse execute(NearbyAccessibilitySummaryRequest request) {
        NearbyObstacleSummary summary =
                obstacleReportService.getNearbySummary(request.lat(), request.lng(), NEARBY_RADIUS_METERS);

        return new NearbyAccessibilitySummaryResponse(
                summary.detourRecommendedCount(),
                summary.cautionCount(),
                summary.safeCount(),
                summary.needsConfirmationCount());
    }
}
