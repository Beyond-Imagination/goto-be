package kr.bi.go_to.controller.place.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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
        @Schema(description = "필터링할 카테고리", example = "관광지") String category) {
    public PlaceSearchRequest {
        k = k == null ? 10 : k;
        category = category == null || category.isBlank() ? null : category.trim();
    }
}
