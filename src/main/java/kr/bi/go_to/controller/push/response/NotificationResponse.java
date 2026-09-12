package kr.bi.go_to.controller.push.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.push.Notification;
import kr.bi.go_to.service.push.PushNotificationType;

@Schema(name = "NotificationResponse", description = "받은 알림 한 건")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "12") long id,
        @Schema(description = "알림 종류") PushNotificationType type,
        @Schema(description = "제목", example = "저장한 장소에 새 소식이 있어요") String title,
        @Schema(description = "본문", example = "국립중앙박물관 · 일부 불편했어요") String body,
        @Schema(description = "누르면 열 화면 경로. 없으면 목록에서만 읽는 알림입니다", example = "/(tabs)/saved") String route,
        @Schema(description = "관련 장소 ID", nullable = true) Long placeId,
        @Schema(description = "관련 제보 ID", nullable = true) Long reportId,
        @Schema(description = "관련 도움 요청 ID", nullable = true) UUID helpRequestId,
        @Schema(description = "읽음 여부") boolean read,
        @Schema(description = "받은 시각") Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getRoute(),
                notification.getPlaceId(),
                notification.getReportId(),
                notification.getHelpRequestId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
