package kr.bi.go_to.service.push;

import java.util.function.Predicate;
import kr.bi.go_to.model.member.MemberPreferences;

/**
 * 보낼 수 있는 푸시의 종류.
 *
 * <p>각 종류는 내 정보 › 알림 설정의 스위치 하나와 1:1로 묶인다. 스위치가 꺼져 있으면
 * 그 회원에게는 이 종류의 푸시를 만들지 않는다(발송 직전에 거르는 것이 아니라 대상 선정에서 뺀다).
 */
public enum PushNotificationType {
    /** 저장한 장소에 새 상태 제보(장소 상태 · 시설 상태)가 올라왔다. */
    SAVED_PLACE_STATUS_CHANGE(MemberPreferences.NotificationSettings::isSavedPlaceStatusChange),

    /** 저장한 장소 주변에 새 장애물 제보가 올라왔다. */
    SAVED_PLACE_NEARBY_OBSTACLE(MemberPreferences.NotificationSettings::isSavedPlaceNearbyObstacle),

    /** 내가 올린 제보를 다른 사람이 "아직 있어요"로 확인했다. */
    MY_REPORT_CONFIRMED(MemberPreferences.NotificationSettings::isMyReportConfirmed),

    /** 내 제보가 오래돼서 지금도 그대로인지 물어본다. */
    MY_REPORT_CONFIRMATION_REQUESTED(MemberPreferences.NotificationSettings::isMyReportConfirmationRequested),

    /** 내 주변에 도움 요청이 올라왔다. */
    NEARBY_HELP_REQUEST(MemberPreferences.NotificationSettings::isNearbyHelpRequest),

    /** 내가 올린 도움 요청을 누군가 수락했다. */
    MY_HELP_REQUEST_ACCEPTED(MemberPreferences.NotificationSettings::isMyHelpRequestAccepted);

    private final Predicate<MemberPreferences.NotificationSettings> enabled;

    PushNotificationType(Predicate<MemberPreferences.NotificationSettings> enabled) {
        this.enabled = enabled;
    }

    /** 이 회원이 이 종류의 알림을 켜 뒀는지. 설정이 아예 없으면(구 계정) 기본값을 따른다. */
    public boolean isEnabledFor(MemberPreferences preferences) {
        if (preferences == null || preferences.getNotificationSettings() == null) {
            return false;
        }
        return enabled.test(preferences.getNotificationSettings());
    }
}
