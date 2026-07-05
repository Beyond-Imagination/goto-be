package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PlaceFilterResponse", description = "장소 탐색에 사용 가능한 필터")
public record PlaceFilterResponse(@Schema(description = "사용 가능한 카테고리 목록") List<String> categories) {
    public PlaceFilterResponse {
        categories = List.copyOf(categories);
    }
}
