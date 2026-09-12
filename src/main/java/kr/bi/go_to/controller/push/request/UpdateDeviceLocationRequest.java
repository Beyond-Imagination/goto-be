package kr.bi.go_to.controller.push.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateDeviceLocationRequest", description = "기기 마지막 위치 갱신 요청")
public record UpdateDeviceLocationRequest(
        @Schema(description = "FCM 등록 토큰", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 512)
                String token,
        @Schema(description = "위도", example = "37.5665", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double latitude,
        @Schema(description = "경도", example = "126.9780", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double longitude) {}
