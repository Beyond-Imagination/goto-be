package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PlaceContactResponse", description = "장소 또는 건물의 공식 연락처 정보")
public record PlaceContactResponse(
        @Schema(description = "장소 ID", example = "1") long placeId,
        @Schema(description = "장소명", example = "국립경주박물관") String placeName,
        @Schema(description = "정제된 주소", nullable = true, example = "경북 경주시 일정로 186") String address,
        @Schema(
                        description = "장소 판별 방식",
                        allowableValues = {"SELECTED_PLACE", "NEARBY_PLACE"})
                String matchType,
        @Schema(description = "장소 위도", nullable = true, example = "35.8294371") Double latitude,
        @Schema(description = "장소 경도", nullable = true, example = "129.2286552") Double longitude,
        @Schema(description = "현재 위치로부터의 거리(m). 선택된 장소 조회이면 null", nullable = true, example = "35") Long distanceMeters,
        @Schema(description = "연락처 제공 가능 여부", example = "true") boolean contactAvailable,
        @Schema(description = "장소에 연결된 공식 연락처 목록") List<ContactMethodResponse> contacts,
        @Schema(description = "장소 공식 홈페이지", nullable = true) String homepage) {

    public PlaceContactResponse {
        contacts = List.copyOf(contacts);
    }
}
