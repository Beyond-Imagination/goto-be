package kr.bi.go_to.model.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.service.push.PushNotificationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원에게 보낸 알림 한 건.
 *
 * <p>푸시는 잠금화면을 지나가면 사라지므로, 앱의 알림 목록이 읽을 수 있도록 같은 내용을 남긴다.
 * 기기 토큰이 없거나 발송이 실패해도 이력은 남는다 — 사용자가 놓친 소식을 나중에 볼 수 있어야 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications")
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PushNotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** 알림을 눌렀을 때 열 화면(expo-router 경로). 푸시 payload의 route와 같다. */
    @Column(length = 200)
    private String route;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "help_request_id")
    private UUID helpRequestId;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification(
            Member member,
            PushNotificationType type,
            String title,
            String body,
            String route,
            Long placeId,
            Long reportId,
            UUID helpRequestId) {
        this.member = member;
        this.type = type;
        this.title = title;
        this.body = body;
        this.route = route;
        this.placeId = placeId;
        this.reportId = reportId;
        this.helpRequestId = helpRequestId;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 이미 읽은 알림은 읽은 시각을 덮어쓰지 않는다 — "언제 처음 봤는지"가 흐려진다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
