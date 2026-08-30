package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;

@Schema(name = "MyConfirmedReportResponse", description = "내가 「아직 있어요」로 확인한 제보 기록")
public record MyConfirmedReportResponse(
        @Schema(description = "확인 기록 식별자", example = "31") Long confirmationId,
        @Schema(description = "내가 확인한 시각", example = "2026-08-21T04:15:30Z") Instant confirmedAt,
        @Schema(description = "확인 대상 제보") MyObstacleReportResponse report) {

    public static MyConfirmedReportResponse from(ObstacleReportConfirmation confirmation, Instant now) {
        return new MyConfirmedReportResponse(
                confirmation.getId(),
                confirmation.getCreatedAt(),
                MyObstacleReportResponse.from(confirmation.getObstacleReport(), now));
    }
}
