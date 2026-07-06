package kr.bi.go_to.controller.help.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "CreateHelpRequestRequest", description = "도움 요청 생성 요청")
public record CreateHelpRequestRequest(
        @Schema(description = "연결할 장소 ID. 길 위 요청이면 null", nullable = true, example = "1") Long placeId,
        @Schema(description = "사용자가 입력한 현재 위치 설명", example = "국립경주박물관 앞 보도") @NotBlank @Size(max = 255)
                String locationLabel,
        @Schema(description = "도움 요청 발생 위도", example = "35.8294371") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
                BigDecimal latitude,
        @Schema(description = "도움 요청 발생 경도", example = "129.2286552")
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                BigDecimal longitude,
        @Schema(description = "층수. 실외 요청이면 null 가능", nullable = true, example = "1") Integer floorLevel,
        @Schema(description = "요청 상세 메시지", nullable = true, example = "보도 턱 앞에서 이동 도움이 필요해요.") @Size(max = 500)
                String message,
        @Schema(description = "요청 만료까지 남은 시간(분). 미입력 시 30분", nullable = true, example = "30") @Min(5) @Max(120)
                Integer expiresInMinutes) {}
