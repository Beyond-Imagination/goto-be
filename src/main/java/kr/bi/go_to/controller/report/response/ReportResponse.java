package kr.bi.go_to.controller.report.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.service.report.model.ReportData;

@Schema(name = "ReportResponse", description = "시설물 상태 제보 응답")
public record ReportResponse(
        @Schema(description = "제보 ID", example = "1") Long id,
        @Schema(description = "제보된 시설물 노드 ID", example = "1") Long nodeId,
        @Schema(description = "시설 유형", example = "ELEVATOR") String nodeType,
        @Schema(description = "시설 이름", nullable = true, example = "본관 엘리베이터") String nodeName,
        @Schema(description = "층 (지하는 음수)", nullable = true, example = "2") Integer floorLevel,
        @Schema(description = "시설이 있는 장소 ID", example = "1247") Long placeId,
        @Schema(description = "시설이 있는 장소명", example = "국립경주박물관") String placeName,
        @Schema(description = "시설 위도", example = "37.523850") double latitude,
        @Schema(description = "시설 경도", example = "126.980470") double longitude,
        @Schema(description = "제보 이슈 유형", example = "BROKEN") String issueType,
        @Schema(description = "제보 상세 내용", nullable = true) String description,
        @Schema(description = "제보 생성 시각") Instant createdAt,
        @Schema(description = "PDR 보정 정보. 체크포인트 노드가 아니면 null", nullable = true) CalibrationResponse calibration) {

    public static ReportResponse from(ReportData report) {
        return new ReportResponse(
                report.id(),
                report.node().id(),
                report.node().nodeType(),
                report.node().name(),
                report.node().floorLevel(),
                report.node().placeId(),
                report.node().placeName(),
                report.node().latitude(),
                report.node().longitude(),
                report.issueType(),
                report.description(),
                report.createdAt(),
                CalibrationResponse.from(report));
    }
}
