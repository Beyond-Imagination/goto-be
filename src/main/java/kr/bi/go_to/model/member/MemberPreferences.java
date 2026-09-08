package kr.bi.go_to.model.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kr.bi.go_to.enums.AvoidCondition;
import kr.bi.go_to.enums.MobilityMode;
import kr.bi.go_to.enums.PriorityFacility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberPreferences {

    private List<MobilityMode> mobilityModes = List.of();

    @JsonProperty("informationPreferences")
    private InformationPreferences informationPreferences = new InformationPreferences();

    /**
     * 알림 설정. 기존 회원의 JSONB에는 이 키가 없으므로 역직렬화 시 기본값(전부 false)이 적용된다.
     */
    private NotificationSettings notificationSettings = new NotificationSettings();

    /**
     * 접근성 보기 설정. 기존 회원의 JSONB에는 이 키가 없으므로 역직렬화 시 기본값(전부 false)이 적용된다.
     */
    private DisplaySettings displaySettings = new DisplaySettings();

    public static MemberPreferences empty() {
        return new MemberPreferences();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InformationPreferences {

        private List<PriorityFacility> priorityFacilities = List.of();
        private List<AvoidCondition> avoidConditions = List.of();
    }

    /**
     * 내 정보 06 화면의 알림 스위치들. 사용자가 직접 켜기 전까지는 모두 꺼진 상태로 시작한다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {

        /** 저장한 장소의 시설 상태가 바뀌면 알림 */
        private boolean savedPlaceStatusChange = false;

        /** 저장한 장소 주변에 새 장애물 제보가 생기면 알림 */
        private boolean savedPlaceNearbyObstacle = false;

        /** 내 제보를 다른 사용자가 확인하면 알림 */
        private boolean myReportConfirmed = false;

        /** 내가 제보한 곳의 정보가 오래되면 확인 요청 알림 */
        private boolean myReportConfirmationRequested = false;

        /** 가까운 곳에서 도움 요청이 생기면 알림 */
        private boolean nearbyHelpRequest = false;

        /** 내 도움 요청을 누군가 수락하면 알림 */
        private boolean myHelpRequestAccepted = false;
    }

    /**
     * 내 정보 07 화면의 접근성 보기 설정.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisplaySettings {

        /** 큰 글씨 */
        private boolean largeText = false;

        /** 고대비 */
        private boolean highContrast = false;

        /** 진동 알림 */
        private boolean vibration = false;

        /** 저장된 장소의 상태 변경 알림 */
        private boolean statusAlerts = false;
    }
}
