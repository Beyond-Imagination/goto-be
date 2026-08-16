package kr.bi.go_to.controller.place.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

@Schema(name = "PlaceSearchRequest", description = "장소 탐색 요청")
public record PlaceSearchRequest(
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
        @Schema(description = "반환할 장소 수(기본 10, 최대 50)", example = "10", defaultValue = "10") @Min(1) @Max(50) Integer k,
        @Schema(description = "필터링할 한국관광공사 현재 분류체계 소분류 코드", example = "A01010100") String categoryCode,
        @Schema(
                        description = "장소유형 필터(다중 선택) — places.category(Tour API cat3, 9자리)에 대한 prefix 매칭. "
                                + "3자리면 대분류(cat1), 5자리면 중분류(cat2) 필터가 된다. "
                                + "DbPlaceService 구현 전까지는 no-op(요청은 받되 필터링은 적용되지 않음, ADR-0004 참고)")
                Set<String> categoryPrefixes,
        @Schema(description = "이동조건 필터(다중 선택). DbPlaceService 구현 전까지는 no-op(ADR-0004 참고)")
                Set<MobilityType> mobilityTypes,
        @Schema(description = "회피구간 필터(다중 선택) — ObstacleIssueType 전체 재사용. DbPlaceService 구현 전까지는 no-op(ADR-0004 참고)")
                Set<ObstacleIssueType> avoid) {
    public PlaceSearchRequest {
        k = k == null ? 10 : k;
        categoryCode = categoryCode == null || categoryCode.isBlank() ? null : categoryCode.trim();
        categoryPrefixes = categoryPrefixes == null ? Set.of() : Set.copyOf(categoryPrefixes);
        mobilityTypes = mobilityTypes == null ? Set.of() : Set.copyOf(mobilityTypes);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
