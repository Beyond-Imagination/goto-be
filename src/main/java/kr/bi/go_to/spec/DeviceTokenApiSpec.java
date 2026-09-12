package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.push.request.RegisterDeviceTokenRequest;
import kr.bi.go_to.controller.push.request.UpdateDeviceLocationRequest;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.PUSH_NAME, description = SwaggerTag.PUSH_DESCRIPTION)
public interface DeviceTokenApiSpec {

    @Operation(
            tags = SwaggerTag.PUSH_NAME,
            summary = "기기 토큰 등록/갱신",
            description =
                    """
                    앱이 FCM 토큰을 받을 때마다 호출합니다(로그인 직후 · 토큰 재발급 · 앱 시작).
                    같은 토큰을 다시 보내면 새로 만들지 않고 주인과 마지막 등록 시각만 갱신하므로 멱등합니다.
                    위치를 함께 보내면 「주변 도움 요청」 푸시 대상 계산에 쓰입니다(선택).
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "등록 성공"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void register(AuthenticatedMember member, RegisterDeviceTokenRequest request);

    @Operation(
            tags = SwaggerTag.PUSH_NAME,
            summary = "기기 마지막 위치 갱신",
            description = "「주변 도움 요청」 푸시는 이 위치를 기준으로 반경을 계산합니다. 내 토큰이 아니면 아무 것도 하지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "갱신 성공"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void updateLocation(AuthenticatedMember member, UpdateDeviceLocationRequest request);

    @Operation(
            tags = SwaggerTag.PUSH_NAME,
            summary = "기기 토큰 해제",
            description = "로그아웃 시 호출합니다. 내 토큰이 아니면 아무 것도 하지 않고 204를 돌려줍니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "해제 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void unregister(AuthenticatedMember member, String token);
}
