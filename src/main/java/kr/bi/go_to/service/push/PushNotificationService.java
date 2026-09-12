package kr.bi.go_to.service.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.properties.PushProperties;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 대상 선정과 발송을 한곳에 모은 서비스.
 *
 * <p>규칙은 두 단계다. (1) 회원의 알림 설정에서 그 종류가 켜져 있어야 하고,
 * (2) 그 회원이 등록한 기기 토큰이 있어야 한다. 발송 뒤 FCM이 "없는 토큰"이라고 답한 것은
 * 바로 지운다 — 그러지 않으면 죽은 토큰이 계속 쌓여 발송 비용과 실패율만 올라간다.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;
    private final PushSender pushSender;
    private final PushProperties properties;
    private final Clock clock;

    public PushNotificationService(
            DeviceTokenRepository deviceTokenRepository,
            MemberRepository memberRepository,
            NotificationService notificationService,
            PushSender pushSender,
            PushProperties properties,
            Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
        this.pushSender = pushSender;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 회원들에게 같은 내용의 푸시를 보낸다.
     *
     * <p>본래 요청(제보 등록 등)은 이미 커밋된 뒤에 호출되므로 새 트랜잭션에서 돈다.
     * 여기서 예외가 나도 앞선 작업을 되돌리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int send(PushNotificationType type, Collection<Long> memberIds, PushMessage message) {
        List<Long> recipients = filterByNotificationSettings(type, memberIds);
        if (recipients.isEmpty()) {
            return 0;
        }

        // 이력을 먼저 남긴다. 기기가 없거나 발송이 실패해도 앱의 알림 목록에서는 볼 수 있어야 한다.
        notificationService.record(recipients, message);

        return dispatch(type, deviceTokenRepository.findByMember_IdIn(recipients), message);
    }

    /** 회원마다 본문이 다른 경우(예: 각자 저장한 장소 이름). 대상이 적을 때만 쓴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sendEach(
            PushNotificationType type, Collection<Long> memberIds, Function<Long, PushMessage> messageFactory) {
        int sent = 0;
        for (Long memberId : filterByNotificationSettings(type, memberIds)) {
            PushMessage message = messageFactory.apply(memberId);
            if (message == null) {
                continue;
            }

            notificationService.record(memberId, message);
            sent += dispatch(type, deviceTokenRepository.findByMember_Id(memberId), message);
        }
        return sent;
    }

    /**
     * 좌표 반경 안에 있는 기기들에 보낸다.
     *
     * <p>"주변"을 저장 장소가 아니라 기기의 마지막 위치로 판단하는 유일한 경로다(주변 도움 요청).
     *
     * @param locationFreshness 이보다 오래된 위치를 보고한 기기는 제외한다. 1단계(즉시)는 짧게,
     *     확대 발송은 길게 준다.
     * @param alreadyNotifiedMemberIds 이미 같은 알림을 받은 회원. 확대 발송에서 중복을 막는다.
     * @return 발송 성공 건수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sendNearby(
            PushNotificationType type,
            double latitude,
            double longitude,
            Long excludedMemberId,
            Duration locationFreshness,
            Collection<Long> alreadyNotifiedMemberIds,
            PushMessage message) {
        Instant locationFreshAfter = Instant.now(clock).minus(locationFreshness);
        Set<Long> alreadyNotified = Set.copyOf(alreadyNotifiedMemberIds);
        List<DeviceToken> nearby = deviceTokenRepository
                .findWithinRadius(
                        latitude,
                        longitude,
                        properties.getHelpRequestRadiusMeters(),
                        locationFreshAfter,
                        excludedMemberId == null ? -1L : excludedMemberId)
                .stream()
                .filter(token -> !alreadyNotified.contains(token.getMember().getId()))
                .toList();

        Set<Long> allowed = Set.copyOf(filterByNotificationSettings(
                type, nearby.stream().map(token -> token.getMember().getId()).toList()));
        List<DeviceToken> targets = nearby.stream()
                .filter(token -> allowed.contains(token.getMember().getId()))
                .toList();

        // 기기 여러 대가 걸려도 사람 기준으로 한 번만 남긴다.
        notificationService.record(allowed, message);

        return dispatch(type, targets, message);
    }

    private int dispatch(PushNotificationType type, List<DeviceToken> targets, PushMessage message) {
        if (targets.isEmpty()) {
            return 0;
        }

        PushSendResult result = pushSender.send(targets, message);
        if (!result.invalidTokens().isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(result.invalidTokens());
            log.info("만료된 기기 토큰 {}건을 정리했습니다.", result.invalidTokens().size());
        }

        log.debug("푸시 발송 (type={}, targets={}, success={})", type, targets.size(), result.successCount());
        return result.successCount();
    }

    /** 알림 설정이 켜진 회원만 남긴다. 설정은 회원 JSONB에 있어 한 번에 읽어 거른다. */
    private List<Long> filterByNotificationSettings(PushNotificationType type, Collection<Long> memberIds) {
        Set<Long> distinct =
                memberIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return List.of();
        }

        Map<Long, Member> members = memberRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        return distinct.stream()
                .filter(memberId -> {
                    Member member = members.get(memberId);
                    return member != null && type.isEnabledFor(member.getPreferences());
                })
                .toList();
    }
}
