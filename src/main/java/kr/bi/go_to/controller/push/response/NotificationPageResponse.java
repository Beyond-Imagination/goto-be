package kr.bi.go_to.controller.push.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.bi.go_to.service.push.NotificationService;

@Schema(name = "NotificationPageResponse", description = "알림 목록 한 페이지")
public record NotificationPageResponse(
        @Schema(description = "알림 목록 (최신순)") List<NotificationResponse> items,
        @Schema(description = "다음 페이지 커서. null이면 마지막 페이지입니다", nullable = true) String nextCursor,
        @Schema(description = "안 읽은 알림 수 (배지에 씁니다)", example = "3") long unreadCount) {

    public static NotificationPageResponse from(NotificationService.NotificationPage page) {
        return new NotificationPageResponse(
                page.items().stream().map(NotificationResponse::from).toList(), page.nextCursor(), page.unreadCount());
    }
}
