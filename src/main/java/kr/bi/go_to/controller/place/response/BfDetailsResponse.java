package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.bi.go_to.service.place.model.BfDetailsData;

@Schema(name = "BfDetailsResponse", description = "장소의 배리어프리 정보")
public record BfDetailsResponse(
        @Schema(description = "엘리베이터 유무") boolean hasElevator,
        @Schema(description = "장애인 화장실 유무") boolean hasAccessibleToilet,
        @Schema(description = "경사로 유무") boolean hasRamp) {
    public static BfDetailsResponse from(BfDetailsData data) {
        if (data == null) {
            return null;
        }
        return new BfDetailsResponse(data.hasElevator(), data.hasAccessibleToilet(), data.hasRamp());
    }
}
