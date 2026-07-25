package kr.bi.go_to.controller.report.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.service.report.model.ReportData;

@Schema(name = "ReportResponse", description = "시설물 상태 제보 응답")
public record ReportResponse(
        @Schema(description = "제보 ID", example = "1") Long id,
        @Schema(description = "제보된 시설물 노드 ID", example = "1") Long nodeId,
        @Schema(description = "제보 이슈 유형", example = "BROKEN") String issueType,
        @Schema(description = "제보 상세 내용", nullable = true) String description,
        @Schema(description = "제보 생성 시각") Instant createdAt,
        @Schema(description = "PDR 보정 정보. 체크포인트 노드가 아니면 null", nullable = true)
                CalibrationResponse calibration) {

    public static ReportResponse from(ReportData report) {
        return new ReportResponse(
                report.id(),
                report.node().id(),
                report.issueType(),
                report.description(),
                report.createdAt(),
                CalibrationResponse.from(report));
    }
}
