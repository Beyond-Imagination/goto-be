package kr.bi.go_to.controller.obstaclereport.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

@Schema(name = "ObstacleReportClusterRequest", description = "장애물 제보 클러스터 조회 요청")
public record ObstacleReportClusterRequest(
        @Schema(description = "조회 영역 최소 위도", example = "37.50", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double minLat,
        @Schema(description = "조회 영역 최소 경도", example = "126.95", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double minLng,
        @Schema(description = "조회 영역 최대 위도", example = "37.55", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double maxLat,
        @Schema(description = "조회 영역 최대 경도", example = "127.00", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double maxLng,
        @Schema(description = "지도 줌 레벨 (클수록 더 가까운 줌)", example = "14", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Min(0)
                @Max(21)
                Integer zoom,
        @Schema(description = "이동조건 필터(다중 선택, 생략 시 전체) — 홈 지도 상단에서 동시에 여러 개 선택 가능 (docs/adr/0006)")
                Set<MobilityType> mobilityTypes,
        @Schema(
                        description =
                                "회피구간 필터(다중 선택, 생략 시 전체) — 이 유형의 제보는 클러스터 결과에서 제외. PlaceSearchRequest.avoid와 동일한 값 집합 재사용")
                Set<ObstacleIssueType> avoid) {
    public ObstacleReportClusterRequest {
        mobilityTypes = mobilityTypes == null ? Set.of() : Set.copyOf(mobilityTypes);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
