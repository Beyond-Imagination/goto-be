package kr.bi.go_to.controller.push;

import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.push.request.NotificationPageRequest;
import kr.bi.go_to.controller.push.response.NotificationPageResponse;
import kr.bi.go_to.controller.push.response.UnreadNotificationCountResponse;
import kr.bi.go_to.service.push.NotificationService;
import kr.bi.go_to.spec.NotificationApiSpec;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 받은 알림 목록 API (저장 04 · 상태 변경 알림 화면). */
@RestController
@RequestMapping("/api/v1/members/me/notifications")
public class NotificationController implements NotificationApiSpec {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @GetMapping
    public NotificationPageResponse findPage(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @ParameterObject @ModelAttribute NotificationPageRequest request) {
        return NotificationPageResponse.from(
                notificationService.findPage(member.id(), request.cursor(), request.sizeOrDefault()));
    }

    @Override
    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse countUnread(@AuthenticationPrincipal AuthenticatedMember member) {
        return new UnreadNotificationCountResponse(notificationService.countUnread(member.id()));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long notificationId) {
        notificationService.markRead(member.id(), notificationId);
    }

    @Override
    @PatchMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedMember member) {
        notificationService.markAllRead(member.id());
    }
}
