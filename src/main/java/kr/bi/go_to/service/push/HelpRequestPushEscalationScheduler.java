package kr.bi.go_to.service.push;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestStatus;
import kr.bi.go_to.properties.PushProperties;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주변 도움 요청 푸시의 2단계 확대 발송.
 *
 * <p>요청이 올라오면 먼저 "방금 위치를 보고한 기기"에만 보낸다(1단계). 그 사람들이 지금 진짜
 * 근처에 있을 가능성이 가장 높아서다. 하지만 그 범위가 늘 비어 있을 수 있으므로, 몇 분 안에
 * 수락이 없으면 위치가 조금 오래된 사람들까지 넓혀 한 번 더 보낸다.
 *
 * <p>덕분에 평소에는 엉뚱한 사람에게 가는 알림이 줄고, 도움이 실제로 필요한 순간에만 범위가 넓어진다.
 * 확대는 요청당 한 번뿐이며, 1단계에서 이미 받은 사람은 제외한다.
 */
@Component
public class HelpRequestPushEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(HelpRequestPushEscalationScheduler.class);

    /** 한 번에 처리할 요청 수. 밀린 요청이 많아도 한 주기를 오래 잡지 않는다. */
    private static final int BATCH_LIMIT = 50;

    private final HelpRequestRepository helpRequestRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final PushProperties properties;
    private final Clock clock;

    public HelpRequestPushEscalationScheduler(
            HelpRequestRepository helpRequestRepository,
            NotificationRepository notificationRepository,
            PushNotificationService pushNotificationService,
            PushProperties properties,
            Clock clock) {
        this.helpRequestRepository = helpRequestRepository;
        this.notificationRepository = notificationRepository;
        this.pushNotificationService = pushNotificationService;
        this.properties = properties;
        this.clock = clock;
    }

    /** 매분 0초. 요청 만료(기본 30분)에 비해 충분히 촘촘하다. */
    @Scheduled(cron = "0 * * * * *")
    public void escalatePendingRequests() {
        int sent = escalate();
        if (sent > 0) {
            log.info("도움 요청 확대 발송 {}건", sent);
        }
    }

    /** 스케줄과 분리해 테스트에서 직접 호출한다. */
    @Transactional
    public int escalate() {
        Instant now = Instant.now(clock);
        List<HelpRequest> pending = helpRequestRepository.findForPushEscalation(
                HelpRequestStatus.REQUESTED, now.minus(properties.getEscalationDelay()), now, Limit.of(BATCH_LIMIT));

        int sent = 0;
        for (HelpRequest request : pending) {
            // 1단계에서 이미 받은 사람에게 같은 알림을 또 보내지 않는다.
            List<Long> alreadyNotified = notificationRepository.findNotifiedMemberIdsByHelpRequest(request.getId());

            sent += pushNotificationService.sendNearby(
                    PushNotificationType.NEARBY_HELP_REQUEST,
                    request.getLatitude().doubleValue(),
                    request.getLongitude().doubleValue(),
                    request.getRequester().getId(),
                    properties.getLocationFreshness(),
                    alreadyNotified,
                    PushMessages.nearbyHelpRequest(
                            request.getId(), request.getLocationLabel(), List.copyOf(request.getKinds())));

            // 보낼 대상이 없었더라도 확대한 것으로 기록한다. 그러지 않으면 매분 같은 요청을 다시 훑는다.
            request.markPushEscalated(now);
        }

        return sent;
    }
}
