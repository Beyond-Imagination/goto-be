package kr.bi.go_to.controller.place.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

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
                Double lng) {}
