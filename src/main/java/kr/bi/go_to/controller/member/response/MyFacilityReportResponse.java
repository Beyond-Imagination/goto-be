package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.report.Report;
import org.locationtech.jts.geom.Point;

@Schema(name = "MyFacilityReportResponse", description = "내 제보 기록의 「시설」 분류 항목")
public record MyFacilityReportResponse(
        @Schema(description = "제보 식별자", example = "77") Long id,
        @Schema(description = "시설 노드 ID", example = "1") Long nodeId,
        @Schema(description = "시설 유형", example = "ELEVATOR") String nodeType,
        @Schema(description = "시설 이름 (없으면 null)", example = "본관 엘리베이터") String nodeName,
        @Schema(description = "층 (지하는 음수)", example = "1") Integer floorLevel,
        @Schema(description = "시설이 있는 장소 ID", example = "1247") Long placeId,
        @Schema(description = "시설이 있는 장소명", example = "국립경주박물관") String placeName,
        @Schema(description = "장소 주소 (없으면 null)", example = "경북 경주시 일정로 186") String address,
        @Schema(description = "시설 위도 (좌표가 없으면 null)", example = "35.8295") Double latitude,
        @Schema(description = "시설 경도 (좌표가 없으면 null)", example = "129.2287") Double longitude,
        @Schema(description = "이슈 유형", example = "BROKEN") String issueType,
        @Schema(description = "제보 상세 내용 (없으면 null)") String description,
        @Schema(description = "제보 작성 시각", example = "2026-08-12T04:15:30Z") Instant createdAt) {

    public static MyFacilityReportResponse from(Report report) {
        FacilityNode node = report.getNode();
        Place place = node.getFloorMap().getPlace();
        Point point = node.getGeojsonPoint();

        return new MyFacilityReportResponse(
                report.getId(),
                node.getId(),
                node.getNodeType(),
                node.getName(),
                node.getFloorMap().getFloorLevel(),
                place.getId(),
                place.getName(),
                place.getSanitizedAddress(),
                point == null ? null : point.getY(),
                point == null ? null : point.getX(),
                report.getIssueType(),
                report.getDescription(),
                report.getCreatedAt());
    }
}
