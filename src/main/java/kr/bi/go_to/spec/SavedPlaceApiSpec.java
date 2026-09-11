package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.savedplace.request.UpdateSavedPlaceNotificationRequest;
import kr.bi.go_to.controller.savedplace.response.SavedPlaceResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.PLACE_NAME, description = SwaggerTag.PLACE_DESCRIPTION)
public interface SavedPlaceApiSpec {

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "내 저장 장소 목록 조회",
            description = "최근 저장한 순으로 내가 저장(즐겨찾기)한 장소 목록을 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = SavedPlaceResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    List<SavedPlaceResponse> findMine(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.PLACE_NAME,
            summary = "저장 장소별 상태 변경 알림 on/off",
            description = "저장한 장소마다 알림을 켜고 끕니다. 받을 알림 종류(시설 상태 변경 · 주변 장애물)는 내 정보의 알림 설정이 정합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "변경 성공",
                content = @Content(schema = @Schema(implementation = SavedPlaceResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "저장하지 않은 장소",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    SavedPlaceResponse updateNotification(
            AuthenticatedMember member, Long placeId, UpdateSavedPlaceNotificationRequest request);
}
