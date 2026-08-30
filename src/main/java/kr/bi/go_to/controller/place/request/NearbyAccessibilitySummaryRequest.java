package kr.bi.go_to.controller.place.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

@Schema(name = "NearbyAccessibilitySummaryRequest", description = "내 주변 접근성 정보 요약 요청")
public record NearbyAccessibilitySummaryRequest(
        @Schema(description = "현재 위치 위도", example = "37.5665", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double lat,
        @Schema(description = "현재 위치 경도", example = "126.9780", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double lng,
        @Schema(description = "이동조건 필터(다중 선택, 생략 시 전체) — 홈 지도 상단 필터와 동일한 값 집합") Set<MobilityType> mobilityTypes,
        @Schema(description = "회피구간 필터(다중 선택, 생략 시 전체) — 이 유형의 제보는 요약 집계에서 제외") Set<ObstacleIssueType> avoid) {
    public NearbyAccessibilitySummaryRequest {
        mobilityTypes = mobilityTypes == null ? Set.of() : Set.copyOf(mobilityTypes);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
