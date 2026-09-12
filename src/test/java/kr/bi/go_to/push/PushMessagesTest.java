package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpKind;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushMessages;
import kr.bi.go_to.service.push.PushNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 알림 문구와 설정 연결 규칙. 스프링 컨텍스트 없이 도는 순수 테스트다. */
class PushMessagesTest {

    @Nested
    @DisplayName("문구")
    class Copy {

        @Test
        @DisplayName("저장 장소 상태 제보는 장소 이름과 상태 라벨을 함께 적는다")
        void savedPlaceStateReported() {
            PushMessage message = PushMessages.savedPlaceStateReported(7L, "국립중앙박물관", PlaceAccessStatus.INACCESSIBLE);

            assertThat(message.type()).isEqualTo(PushNotificationType.SAVED_PLACE_STATUS_CHANGE);
            assertThat(message.body()).isEqualTo("국립중앙박물관 · 이용 어려웠어요");
            assertThat(message.toFcmData())
                    .containsEntry(PushMessage.DATA_ROUTE, "/(tabs)/saved")
                    .containsEntry(PushMessage.DATA_TYPE, "SAVED_PLACE_STATUS_CHANGE")
                    .containsEntry("placeId", "7");
        }

        @Test
        @DisplayName("시설 제보는 시설 이름이 없으면 「시설」로 적는다")
        void facilityReportedWithoutName() {
            PushMessage message = PushMessages.savedPlaceFacilityReported(1L, "서울역", null, "고장");

            assertThat(message.body()).isEqualTo("서울역 · 시설 고장");
        }

        @Test
        @DisplayName("시설 이슈 라벨은 모르는 값이면 「상태 변경」으로 떨어진다")
        void facilityIssueLabelFallback() {
            assertThat(PushMessages.facilityIssueLabel("BROKEN")).isEqualTo("고장");
            assertThat(PushMessages.facilityIssueLabel("LEGACY_VALUE")).isEqualTo("상태 변경");
            assertThat(PushMessages.facilityIssueLabel(null)).isEqualTo("상태 변경");
        }

        @Test
        @DisplayName("주변 장애물은 저장 장소를 모르면 「저장한 장소 근처」로 적는다")
        void nearbyObstacleWithoutPlaceName() {
            PushMessage message = PushMessages.savedPlaceNearbyObstacle(
                    3L, null, ObstacleIssueType.ILLEGAL_PARKING, ObstacleSeverity.CAUTION);

            assertThat(message.title()).isEqualTo("저장한 장소 근처에 새 장애물 제보");
            assertThat(message.body()).isEqualTo("불법 주차 · 통행 주의");
            assertThat(message.toFcmData()).containsEntry(PushMessage.DATA_ROUTE, "/report/detail");
        }

        @Test
        @DisplayName("확인 알림은 확인한 사람 수를 적는다")
        void confirmed() {
            PushMessage message = PushMessages.myReportConfirmed(9L, ObstacleIssueType.STAIRS, 3);

            assertThat(message.body()).isEqualTo("계단 제보를 3명이 확인했어요");
        }

        @Test
        @DisplayName("확인 요청은 며칠이 지났는지 적는다")
        void confirmationRequested() {
            PushMessage message =
                    PushMessages.myReportConfirmationRequested(9L, ObstacleIssueType.SLIPPERY_SURFACE, 45);

            assertThat(message.title()).isEqualTo("이 제보, 지금도 그대로인가요?");
            assertThat(message.body()).isEqualTo("미끄러운 길 제보를 확인한 지 45일이 지났어요");
        }

        @Test
        @DisplayName("도움 유형이 셋 이상이면 두 개만 적고 나머지는 개수로 줄인다")
        void helpKindsAreTruncated() {
            PushMessage message = PushMessages.nearbyHelpRequest(
                    UUID.randomUUID(),
                    "시청 앞 보도",
                    List.of(HelpKind.MOBILITY_ASSIST, HelpKind.DOOR_ASSIST, HelpKind.WAYFINDING, HelpKind.OTHER));

            assertThat(message.body()).isEqualTo("시청 앞 보도 · 이동 보조 · 문 열기 외 2건");
        }

        @Test
        @DisplayName("도움 유형이 비어 있어도 문구가 깨지지 않는다")
        void helpKindsEmpty() {
            PushMessage message = PushMessages.nearbyHelpRequest(UUID.randomUUID(), "어딘가", List.of());

            assertThat(message.body()).isEqualTo("어딘가 · 도움 요청");
        }

        @Test
        @DisplayName("수락 알림은 도우미 닉네임을 적고 요청 대기 화면으로 보낸다")
        void accepted() {
            UUID id = UUID.randomUUID();
            PushMessage message = PushMessages.myHelpRequestAccepted(id, "도우미");

            assertThat(message.body()).isEqualTo("도우미님이 도와주러 가고 있어요");
            assertThat(message.toFcmData())
                    .containsEntry(PushMessage.DATA_ROUTE, "/help/request-pending")
                    .containsEntry("helpRequestId", id.toString());
        }

        @Test
        @DisplayName("data에 null 값은 담기지 않는다 — FCM이 거부한다")
        void dropsNullData() {
            PushMessage message = PushMessage.of(PushNotificationType.MY_REPORT_CONFIRMED, "제목", "본문")
                    .data("nothing", null)
                    .build();

            assertThat(message.toFcmData()).doesNotContainKey("nothing");
        }
    }

    @Nested
    @DisplayName("알림 설정 연결")
    class Settings {

        @Test
        @DisplayName("종류마다 대응하는 스위치 하나만 본다")
        void eachTypeReadsItsOwnSwitch() {
            MemberPreferences preferences = MemberPreferences.empty();
            MemberPreferences.NotificationSettings settings = preferences.getNotificationSettings();
            settings.setSavedPlaceStatusChange(false);
            settings.setNearbyHelpRequest(false);
            settings.setMyReportConfirmed(true);

            assertThat(PushNotificationType.MY_REPORT_CONFIRMED.isEnabledFor(preferences))
                    .isTrue();
            assertThat(PushNotificationType.SAVED_PLACE_STATUS_CHANGE.isEnabledFor(preferences))
                    .isFalse();
            assertThat(PushNotificationType.NEARBY_HELP_REQUEST.isEnabledFor(preferences))
                    .isFalse();
        }

        @Test
        @DisplayName("설정을 건드린 적 없는 회원은 모든 종류가 켜져 있다")
        void defaultsToEnabled() {
            MemberPreferences preferences = MemberPreferences.empty();

            for (PushNotificationType type : PushNotificationType.values()) {
                assertThat(type.isEnabledFor(preferences)).as(type.name()).isTrue();
            }
        }

        @Test
        @DisplayName("설정이 없는 회원에게는 보내지 않는다")
        void missingPreferencesMeansDisabled() {
            assertThat(PushNotificationType.MY_REPORT_CONFIRMED.isEnabledFor(null))
                    .isFalse();
            assertThat(PushNotificationType.MY_REPORT_CONFIRMED.isEnabledFor(new MemberPreferences(
                            List.of(), new MemberPreferences.InformationPreferences(), null, null)))
                    .isFalse();
        }
    }
}
