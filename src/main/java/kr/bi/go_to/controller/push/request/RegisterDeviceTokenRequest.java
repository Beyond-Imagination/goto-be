package kr.bi.go_to.controller.push.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.bi.go_to.model.push.DevicePlatform;

@Schema(name = "RegisterDeviceTokenRequest", description = "FCM 기기 토큰 등록/갱신 요청")
public record RegisterDeviceTokenRequest(
        @Schema(description = "FCM 등록 토큰", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 512)
                String token,
        @Schema(description = "기기 플랫폼", example = "ANDROID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                DevicePlatform platform,
        @Schema(description = "앱 버전", example = "0.1.0") @Size(max = 50) String appVersion,
        @Schema(description = "마지막 위치 위도. 위치 권한이 없으면 생략한다.", example = "37.5665")
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double latitude,
        @Schema(description = "마지막 위치 경도. 위치 권한이 없으면 생략한다.", example = "126.9780")
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double longitude) {}
