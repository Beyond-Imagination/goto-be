package kr.bi.go_to.controller.report.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.service.report.model.ReportData;

@Schema(name = "CalibrationResponse", description = "PDR calibration data confirmed by a checkpoint report")
public record CalibrationResponse(
        @Schema(description = "Time when the user's indoor position is confirmed") Instant confirmedAt,
        @Schema(description = "Checkpoint node ID", example = "1") Long nodeId,
        @Schema(description = "Checkpoint latitude", example = "37.523850") double latitude,
        @Schema(description = "Checkpoint longitude", example = "126.980470") double longitude,
        @Schema(description = "Indoor floor level", example = "2") Integer floorLevel,
        @Schema(description = "Allowed snap radius in meters", nullable = true, example = "5") Integer snapRadius) {

    public static CalibrationResponse from(ReportData report) {
        if (!report.node().isCheckpoint()) {
            return null;
        }
        return new CalibrationResponse(
                report.createdAt(),
                report.node().id(),
                report.node().latitude(),
                report.node().longitude(),
                report.node().floorLevel(),
                report.node().snapRadius());
    }
}
