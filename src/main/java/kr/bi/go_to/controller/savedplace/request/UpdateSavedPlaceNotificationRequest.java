package kr.bi.go_to.controller.savedplace.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateSavedPlaceNotificationRequest", description = "저장 장소별 상태 변경 알림 on/off 요청")
public record UpdateSavedPlaceNotificationRequest(
        @Schema(description = "이 장소의 상태 변경 알림을 받을지 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Boolean enabled) {}
