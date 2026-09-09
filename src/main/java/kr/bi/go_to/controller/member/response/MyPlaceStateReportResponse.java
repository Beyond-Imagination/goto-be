package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;

@Schema(name = "MyPlaceStateReportResponse", description = "내 제보 기록의 「장소」 분류 항목")
public record MyPlaceStateReportResponse(
        @Schema(description = "제보 식별자", example = "31") Long id,
        @Schema(description = "장소 ID", example = "1247") Long placeId,
        @Schema(description = "장소명", example = "서울숲 공원") String placeName,
        @Schema(description = "장소 주소 (없으면 null)", example = "서울 성동구 뚝섬로 273") String address,
        @Schema(description = "장소 위도 (좌표가 없으면 null)", example = "37.5665") Double latitude,
        @Schema(description = "장소 경도 (좌표가 없으면 null)", example = "126.978") Double longitude,
        @Schema(description = "장소 전반의 이용 난이도", example = "PARTIALLY_ACCESSIBLE") PlaceAccessStatus accessStatus,
        @Schema(description = "편의시설별 상태") Map<PriorityFacility, PlaceFacilityStatus> facilityStatuses,
        @Schema(description = "첨부 사진 URL 목록", example = "[]") List<String> photoUrls,
        @Schema(description = "제보자가 남긴 메모 (없으면 null)") String description,
        @Schema(description = "제보 작성 시각", example = "2026-08-12T04:15:30Z") Instant createdAt) {

    public static MyPlaceStateReportResponse from(PlaceStateReport report) {
        Place place = report.getPlace();

        return new MyPlaceStateReportResponse(
                report.getId(),
                place.getId(),
                place.getName(),
                place.getSanitizedAddress(),
                place.getLocationPoint() == null
                        ? null
                        : place.getLocationPoint().getY(),
                place.getLocationPoint() == null
                        ? null
                        : place.getLocationPoint().getX(),
                report.getAccessStatus(),
                // 지연 로딩 컬렉션을 그대로 담으면 트랜잭션 종료 후 직렬화에서 LazyInitializationException이 난다.
                Map.copyOf(report.getFacilityStatuses()),
                List.copyOf(report.getPhotoUrls()),
                report.getDescription(),
                report.getCreatedAt());
    }
}
