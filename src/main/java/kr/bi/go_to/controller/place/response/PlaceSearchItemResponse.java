package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PlaceSearchItemResponse", description = "장소 탐색 결과 항목")
public record PlaceSearchItemResponse(
        @Schema(description = "장소 ID", example = "1") long placeId,
        @Schema(description = "장소명", example = "국립중앙박물관") String name,
        @Schema(description = "카테고리", example = "관광지") String category,
        @Schema(description = "정제된 주소", example = "서울 용산구 서빙고로 137") String address,
        @Schema(description = "썸네일 URL") String thumbnailUrl,
        @Schema(description = "위도", example = "37.523850") double latitude,
        @Schema(description = "경도", example = "126.980470") double longitude,
        @Schema(description = "현재 위치로부터의 거리(m)", example = "4740.2") double distanceMeters,
        BfDetailsResponse bfDetails) {}
