package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.push.request.NotificationPageRequest;
import kr.bi.go_to.controller.push.response.NotificationPageResponse;
import kr.bi.go_to.controller.push.response.UnreadNotificationCountResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;

@Tag(name = SwaggerTag.PUSH_NAME, description = SwaggerTag.PUSH_DESCRIPTION)
public interface NotificationApiSpec {

    @Operation(
            tags = SwaggerTag.PUSH_NAME,
            summary = "받은 알림 목록",
            description =
                    """
                    푸시로 보낸 알림을 최신순으로 돌려줍니다. 기기 토큰이 없거나 발송이 실패했어도
                    대상이었다면 목록에는 남습니다. 커서는 응답의 nextCursor를 그대로 돌려주면 됩니다.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = NotificationPageResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "커서 형식 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    NotificationPageResponse findPage(AuthenticatedMember member, NotificationPageRequest request);

    @Operation(tags = SwaggerTag.PUSH_NAME, summary = "안 읽은 알림 수", description = "저장 탭 벨 배지에 씁니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = UnreadNotificationCountResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    UnreadNotificationCountResponse countUnread(AuthenticatedMember member);

    @Operation(tags = SwaggerTag.PUSH_NAME, summary = "알림 하나 읽음 처리")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "처리 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "내 알림이 아니거나 없는 알림",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void markRead(AuthenticatedMember member, Long notificationId);

    @Operation(tags = SwaggerTag.PUSH_NAME, summary = "알림 모두 읽음 처리")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "처리 성공"),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    void markAllRead(AuthenticatedMember member);
}
