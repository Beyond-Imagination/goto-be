package kr.bi.go_to.controller.savedplace.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import kr.bi.go_to.controller.place.response.BfDetailsResponse;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.savedplace.SavedPlace;

@Schema(name = "SavedPlaceResponse", description = "저장 장소 목록 항목")
public record SavedPlaceResponse(
        @Schema(description = "장소 ID", example = "1") long placeId,
        @Schema(description = "장소명", example = "국립중앙박물관") String name,
        @Schema(description = "카테고리", example = "관광지") String category,
        @Schema(description = "정제된 주소", example = "서울 용산구 서빙고로 137") String address,
        @Schema(description = "썸네일 URL") String thumbnailUrl,
        @Schema(description = "위도", example = "37.523850") double latitude,
        @Schema(description = "경도", example = "126.980470") double longitude,
        BfDetailsResponse bfDetails,
        @Schema(description = "실내 지도(FLOOR_MAP) 존재 여부") boolean hasIndoorMap,
        @Schema(description = "저장 당시 장소가 이후 소프트 삭제되지 않고 여전히 이용 가능한지 여부") boolean isAvailable,
        @Schema(description = "저장한 일시") Instant savedAt) {

    public static SavedPlaceResponse from(SavedPlace savedPlace, boolean hasIndoorMap) {
        Place place = savedPlace.getPlace();
        return new SavedPlaceResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getSanitizedAddress(),
                place.getThumbnailUrl(),
                place.getLocationPoint().getY(),
                place.getLocationPoint().getX(),
                null,
                hasIndoorMap,
                !place.isDeleted(),
                savedPlace.getCreatedAt());
    }
}
