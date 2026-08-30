package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.member.request.UpdateMyPreferencesRequest;
import kr.bi.go_to.controller.member.request.UpdateMySettingsRequest;
import kr.bi.go_to.controller.member.response.MyConfirmedReportResponse;
import kr.bi.go_to.controller.member.response.MyObstacleReportResponse;
import kr.bi.go_to.controller.member.response.MyPreferencesResponse;
import kr.bi.go_to.controller.member.response.MyProfileResponse;
import kr.bi.go_to.controller.member.response.MySettingsResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.MY_PAGE_NAME, description = SwaggerTag.MY_PAGE_DESCRIPTION)
public interface MyPageApiSpec {

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "내 프로필 요약 및 활동 통계 조회",
            description = "내 정보 홈 화면에 필요한 닉네임, 이동 방식, 활동 통계(제보 수 · 확인해 준 사람 수 · 해결 확인 수)를 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = MyProfileResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    MyProfileResponse getProfile(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "접근성 프로필 조회",
            description = "이동 방식, 우선 확인 시설, 피하고 싶은 조건을 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = MyPreferencesResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    MyPreferencesResponse getPreferences(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "접근성 프로필 수정",
            description = "전달한 값으로 전체 교체합니다. 우선 확인 시설과 피하고 싶은 조건은 각각 최대 3개입니다. 알림·보기 설정은 변경되지 않습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = @Content(schema = @Schema(implementation = MyPreferencesResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패 (최대 개수 초과 등)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    MyPreferencesResponse updatePreferences(AuthenticatedMember member, UpdateMyPreferencesRequest request);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "알림 설정 및 접근성 보기 설정 조회",
            description = "내 정보 06 · 07 화면의 스위치 상태를 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = MySettingsResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    MySettingsResponse getSettings(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "알림 설정 및 접근성 보기 설정 수정",
            description = "전달한 값으로 전체 교체합니다. 접근성 프로필은 변경되지 않습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "수정 성공",
                content = @Content(schema = @Schema(implementation = MySettingsResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    MySettingsResponse updateSettings(AuthenticatedMember member, UpdateMySettingsRequest request);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "내가 작성한 장애물 제보 목록 조회",
            description = "최신순으로 반환합니다. 페이지네이션은 아직 도입되지 않았습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = MyObstacleReportResponse.class)))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    List<MyObstacleReportResponse> findMyObstacleReports(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.MY_PAGE_NAME,
            summary = "내가 확인한 제보 목록 조회",
            description = "내가 「아직 있어요」로 확인한 제보를 최신순으로 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = MyConfirmedReportResponse.class)))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    List<MyConfirmedReportResponse> findMyConfirmedReports(AuthenticatedMember member);
}
