package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PlaceSearchResponse", description = "장소 탐색 응답")
public record PlaceSearchResponse(List<PlaceSearchItemResponse> places, PlaceFilterResponse filters) {
    public PlaceSearchResponse {
        places = List.copyOf(places);
    }
}
