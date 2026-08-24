package kr.bi.go_to.controller.obstaclereport.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateObstacleReportStatusRequest", description = "장애물 제보 상태 변경 요청")
public record UpdateObstacleReportStatusRequest(
        @Schema(
                        description = "액션 (STILL_PRESENT: 아직 있어요, RESOLVED: 해결됐어요)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                Action action) {

    public enum Action {
        STILL_PRESENT,
        RESOLVED,
    }
}
