package kr.bi.go_to.service.push;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.push.Notification;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.NotificationRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 이력.
 *
 * <p>푸시는 잠금화면을 지나가면 사라지므로 같은 내용을 여기에 남기고, 앱의 알림 목록이 이걸 읽는다.
 * 기기 토큰이 없거나 발송이 실패해도 이력은 남긴다 — 놓친 소식을 나중에 볼 수 있어야 한다.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository, MemberRepository memberRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /** 회원들에게 같은 알림을 남긴다. 대상 선정(설정·반경)은 호출부가 이미 끝냈다고 본다. */
    @Transactional
    public void record(Collection<Long> memberIds, PushMessage message) {
        if (memberIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = memberRepository.findAllById(memberIds).stream()
                .map(member -> toNotification(member, message))
                .toList();

        notificationRepository.saveAll(notifications);
    }

    /** 회원 한 명에게 남긴다. 회원마다 본문이 다른 알림(주변 장애물)에 쓴다. */
    @Transactional
    public void record(Long memberId, PushMessage message) {
        record(List.of(memberId), message);
    }

    @Transactional(readOnly = true)
    public NotificationPage findPage(Long memberId, String cursor, int size) {
        NotificationCursor decoded = NotificationCursor.decode(cursor);
        // 한 건 더 읽어 다음 페이지가 있는지 본다(개수를 따로 세지 않기 위해서다).
        Limit limit = Limit.of(size + 1);

        List<Notification> found = decoded == null
                ? notificationRepository.findFirstPage(memberId, limit)
                : notificationRepository.findPageAfter(memberId, decoded.createdAt(), decoded.id(), limit);

        boolean hasNext = found.size() > size;
        List<Notification> items = hasNext ? found.subList(0, size) : found;
        Notification last = items.isEmpty() ? null : items.get(items.size() - 1);

        return new NotificationPage(
                items,
                hasNext && last != null ? new NotificationCursor(last.getCreatedAt(), last.getId()).encode() : null,
                notificationRepository.countByMember_IdAndReadAtIsNull(memberId));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long memberId) {
        return notificationRepository.countByMember_IdAndReadAtIsNull(memberId);
    }

    /** 알림 하나를 읽음 처리한다. 남의 알림은 찾을 수 없는 것으로 다룬다. */
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(found -> found.isOwnedBy(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markRead(Instant.now(clock));
    }

    /** 「모두 읽음」. 읽은 건수를 돌려준다. */
    @Transactional
    public int markAllRead(Long memberId) {
        return notificationRepository.markAllRead(memberId, Instant.now(clock));
    }

    private Notification toNotification(Member member, PushMessage message) {
        Map<String, String> data = message.toFcmData();

        return new Notification(
                member,
                message.type(),
                message.title(),
                message.body(),
                data.get(PushMessage.DATA_ROUTE),
                parseLong(data.get("placeId")),
                parseLong(data.get("id")),
                parseUuid(data.get("helpRequestId")));
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 목록 한 페이지. */
    public record NotificationPage(List<Notification> items, String nextCursor, long unreadCount) {}
}
