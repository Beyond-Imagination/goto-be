package kr.bi.go_to.service.push;

import java.util.List;
import kr.bi.go_to.properties.PushProperties;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.service.push.event.FacilityReportedEvent;
import kr.bi.go_to.service.push.event.HelpRequestAcceptedEvent;
import kr.bi.go_to.service.push.event.HelpRequestCreatedEvent;
import kr.bi.go_to.service.push.event.ObstacleReportConfirmedEvent;
import kr.bi.go_to.service.push.event.ObstacleReportedEvent;
import kr.bi.go_to.service.push.event.PlaceStateReportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 푸시로 옮기는 자리.
 *
 * <p>모두 커밋 이후(AFTER_COMMIT)에 처리한다. 롤백된 제보·요청으로 알림이 나가면 되돌릴 수 없고,
 * 발송이 느려도 원래 요청의 응답이 밀리지 않아야 하기 때문이다. 여기서 예외가 나도 본래 작업은
 * 이미 끝나 있으므로 로그만 남기고 삼킨다.
 */
@Component
public class PushNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationEventListener.class);

    private final PushNotificationService pushNotificationService;
    private final SavedPlaceRepository savedPlaceRepository;
    private final PushProperties properties;

    public PushNotificationEventListener(
            PushNotificationService pushNotificationService,
            SavedPlaceRepository savedPlaceRepository,
            PushProperties properties) {
        this.pushNotificationService = pushNotificationService;
        this.savedPlaceRepository = savedPlaceRepository;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlaceStateReported(PlaceStateReportedEvent event) {
        safely(
                "장소 상태 제보",
                () -> pushNotificationService.send(
                        PushNotificationType.SAVED_PLACE_STATUS_CHANGE,
                        recipientsOfPlace(event.placeId(), event.reporterId()),
                        PushMessages.savedPlaceStateReported(
                                event.placeId(), event.placeName(), event.accessStatus())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFacilityReported(FacilityReportedEvent event) {
        safely(
                "시설 상태 제보",
                () -> pushNotificationService.send(
                        PushNotificationType.SAVED_PLACE_STATUS_CHANGE,
                        recipientsOfPlace(event.placeId(), event.reporterId()),
                        PushMessages.savedPlaceFacilityReported(
                                event.placeId(), event.placeName(), event.facilityName(), event.issueLabel())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObstacleReported(ObstacleReportedEvent event) {
        safely("장애물 제보", () -> {
            List<Long> recipients = savedPlaceRepository
                    .findNotifiableMemberIdsNearby(
                            event.latitude(), event.longitude(), properties.getSavedPlaceObstacleRadiusMeters())
                    .stream()
                    .filter(memberId -> !memberId.equals(event.reporterId()))
                    .toList();

            // 본문에 "어느 저장 장소 근처인지"를 적어야 해서 회원마다 메시지를 따로 만든다.
            return pushNotificationService.sendEach(
                    PushNotificationType.SAVED_PLACE_NEARBY_OBSTACLE,
                    recipients,
                    memberId -> PushMessages.savedPlaceNearbyObstacle(
                            event.reportId(),
                            savedPlaceRepository
                                    .findNearestSavedPlaceName(
                                            memberId,
                                            event.latitude(),
                                            event.longitude(),
                                            properties.getSavedPlaceObstacleRadiusMeters())
                                    .orElse(null),
                            event.issueType(),
                            event.severity()));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onObstacleReportConfirmed(ObstacleReportConfirmedEvent event) {
        if (event.reporterId().equals(event.confirmedByMemberId())) {
            return;
        }

        safely(
                "제보 확인",
                () -> pushNotificationService.send(
                        PushNotificationType.MY_REPORT_CONFIRMED,
                        List.of(event.reporterId()),
                        PushMessages.myReportConfirmed(event.reportId(), event.issueType(), event.confirmedCount())));
    }

    /**
     * 1단계 발송 — 방금 위치를 보고한 기기에만 보낸다.
     * 수락이 없으면 HelpRequestPushEscalationScheduler가 더 넓은 대상으로 한 번 더 보낸다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHelpRequestCreated(HelpRequestCreatedEvent event) {
        safely(
                "도움 요청 생성",
                () -> pushNotificationService.sendNearby(
                        PushNotificationType.NEARBY_HELP_REQUEST,
                        event.latitude(),
                        event.longitude(),
                        event.requesterId(),
                        properties.getImmediateLocationFreshness(),
                        List.of(),
                        PushMessages.nearbyHelpRequest(event.helpRequestId(), event.locationLabel(), event.kinds())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHelpRequestAccepted(HelpRequestAcceptedEvent event) {
        safely(
                "도움 요청 수락",
                () -> pushNotificationService.send(
                        PushNotificationType.MY_HELP_REQUEST_ACCEPTED,
                        List.of(event.requesterId()),
                        PushMessages.myHelpRequestAccepted(event.helpRequestId(), event.helperNickname())));
    }

    /** 이 장소를 저장하고 장소별 알림도 켜 둔 회원들 (제보자 본인 제외). */
    private List<Long> recipientsOfPlace(Long placeId, Long reporterId) {
        return savedPlaceRepository.findNotifiableMemberIdsByPlace(placeId).stream()
                .filter(memberId -> !memberId.equals(reporterId))
                .toList();
    }

    private void safely(String what, PushTask task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.error("{} 푸시 발송 중 오류", what, exception);
        }
    }

    @FunctionalInterface
    private interface PushTask {
        int run();
    }
}
