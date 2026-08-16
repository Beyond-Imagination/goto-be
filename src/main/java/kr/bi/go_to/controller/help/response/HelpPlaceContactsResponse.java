package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "HelpPlaceContactsResponse", description = "현재 위치 또는 선택 장소의 도움 연락처 정보")
public record HelpPlaceContactsResponse(
        @Schema(description = "긴급 상황에서 우선 노출할 연락처") ContactMethodResponse emergencyContact,
        @Schema(description = "선택한 장소 또는 현재 위치 주변의 장소별 연락처") List<PlaceContactResponse> placeContacts) {

    public HelpPlaceContactsResponse {
        placeContacts = List.copyOf(placeContacts);
    }
}
