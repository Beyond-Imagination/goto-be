package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.enums.MyReportKind;

/**
 * 내 제보 기록 목록의 한 항목.
 *
 * <p>분류마다 필요한 필드가 달라서 공통 필드(kind·createdAt)만 위로 올리고, 본문은 분류별
 * 객체 하나에만 담는다. kind가 OBSTACLE이면 obstacle만 채워지고 나머지는 null이다.
 */
@Schema(name = "MyReportItemResponse", description = "내 제보 기록 목록 항목 (분류별 본문 중 하나만 채워진다)")
public record MyReportItemResponse(
        @Schema(description = "제보 분류") MyReportKind kind,
        @Schema(description = "제보 작성 시각 (목록 정렬 기준)") Instant createdAt,
        @Schema(description = "길 위 장애물 제보 본문", nullable = true) MyObstacleReportResponse obstacle,
        @Schema(description = "장소 상태 제보 본문", nullable = true) MyPlaceStateReportResponse place,
        @Schema(description = "시설 상태 제보 본문", nullable = true) MyFacilityReportResponse facility) {

    public static MyReportItemResponse ofObstacle(MyObstacleReportResponse obstacle) {
        return new MyReportItemResponse(MyReportKind.OBSTACLE, obstacle.createdAt(), obstacle, null, null);
    }

    public static MyReportItemResponse ofPlace(MyPlaceStateReportResponse place) {
        return new MyReportItemResponse(MyReportKind.PLACE, place.createdAt(), null, place, null);
    }

    public static MyReportItemResponse ofFacility(MyFacilityReportResponse facility) {
        return new MyReportItemResponse(MyReportKind.FACILITY, facility.createdAt(), null, null, facility);
    }

    /** 커서를 만들 때 쓰는 분류 안의 식별자. */
    public long id() {
        return switch (kind) {
            case OBSTACLE -> obstacle.id();
            case PLACE -> place.id();
            case FACILITY -> facility.id();
        };
    }
}
