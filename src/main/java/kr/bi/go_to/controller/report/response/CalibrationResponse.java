package kr.bi.go_to.controller.report.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.service.report.model.ReportData;

@Schema(name = "CalibrationResponse", description = "체크포인트 제보로 확인된 PDR 보정 정보")
public record CalibrationResponse(
        @Schema(description = "사용자 실내 위치가 확인된 시각") Instant confirmedAt,
        @Schema(description = "체크포인트 위도", example = "37.523850") double latitude,
        @Schema(description = "체크포인트 경도", example = "126.980470") double longitude,
        @Schema(description = "실내 층수", example = "2") Integer floorLevel,
        @Schema(description = "허용 스냅 반경(m)", nullable = true, example = "5") Integer snapRadius) {

    public static CalibrationResponse from(ReportData report) {
        if (!report.node().isCheckpoint()) {
            return null;
        }
        return new CalibrationResponse(
                report.createdAt(),
                report.node().latitude(),
                report.node().longitude(),
                report.node().floorLevel(),
                report.node().snapRadius());
    }
}
