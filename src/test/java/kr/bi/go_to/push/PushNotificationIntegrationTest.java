package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.bi.go_to.controller.help.request.CreateHelpRequestRequest;
import kr.bi.go_to.controller.obstaclereport.request.CreateObstacleReportRequest;
import kr.bi.go_to.controller.obstaclereport.request.UpdateObstacleReportStatusRequest;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.help.HelpKind;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.model.savedplace.SavedPlace;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.service.HelpRequestService;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import kr.bi.go_to.service.placereport.PlaceStateReportService;
import kr.bi.go_to.service.push.DeviceTokenService;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushNotificationType;
import kr.bi.go_to.support.PushTestConfiguration;
import kr.bi.go_to.support.RecordingPushSender;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 이벤트 → 푸시 대상 선정까지의 전체 흐름.
 *
 * <p>발송 채널만 기록용으로 바꿔 끼우고(PushTestConfiguration) 나머지는 실제 서비스·DB를 쓴다.
 * "누가 받고 누가 못 받는가"가 이 기능의 전부라, 대상 선정 규칙을 조건별로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PushTestConfiguration.class})
class PushNotificationIntegrationTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /** 서울시청 좌표. 반경 판정의 기준점으로 쓴다. */
    private static final double BASE_LAT = 37.5665;

    private static final double BASE_LNG = 126.9780;

    @Autowired
    RecordingPushSender pushSender;

    @Autowired
    DeviceTokenService deviceTokenService;

    @Autowired
    PlaceStateReportService placeStateReportService;

    @Autowired
    ObstacleReportService obstacleReportService;

    @Autowired
    HelpRequestService helpRequestService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    SavedPlaceRepository savedPlaceRepository;

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    HelpRequestRepository helpRequestRepository;

    @Autowired
    DeviceTokenRepository deviceTokenRepository;

    @BeforeEach
    void setUp() {
        pushSender.clear();
        deviceTokenRepository.deleteAll();
        helpRequestRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        placeStateReportRepository.deleteAll();
        savedPlaceRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("장소 상태 제보가 올라오면 그 장소를 저장한 사람에게 푸시가 간다")
    void notifiesSaversWhenPlaceStateReported() {
        Member reporter = member("제보자", all(true));
        Member saver = member("저장한사람", all(true));
        Place place = place("국립중앙박물관");
        save(saver, place, true);
        String token = registerDevice(saver, "token-saver");

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        List<PushMessage> messages = pushSender.messagesTo(token);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.SAVED_PLACE_STATUS_CHANGE);
        assertThat(messages.get(0).body()).contains("국립중앙박물관");
        assertThat(messages.get(0).toFcmData())
                .containsEntry(PushMessage.DATA_ROUTE, "/(tabs)/saved")
                .containsEntry("placeId", String.valueOf(place.getId()));
    }

    @Test
    @DisplayName("제보자 본인이 저장한 장소여도 자기 제보로는 푸시를 받지 않는다")
    void doesNotNotifyReporterOfOwnReport() {
        Member reporter = member("제보자", all(true));
        Place place = place("서울숲");
        save(reporter, place, true);
        String token = registerDevice(reporter, "token-reporter");

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("알림 설정에서 「저장한 장소 상태 변경」을 꺼 두면 푸시를 받지 않는다")
    void respectsNotificationSettings() {
        Member reporter = member("제보자", all(true));
        Member saver = member("설정끈사람", all(false));
        Place place = place("광화문광장");
        save(saver, place, true);
        String token = registerDevice(saver, "token-off");

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("장소별 알림을 끈 저장 장소는 전역 설정이 켜져 있어도 푸시를 보내지 않는다")
    void respectsPerPlaceNotificationSwitch() {
        Member reporter = member("제보자", all(true));
        Member saver = member("장소별끔", all(true));
        Place place = place("남산타워");
        save(saver, place, false);
        String token = registerDevice(saver, "token-place-off");

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("기기 토큰이 없으면 대상이어도 발송하지 않는다")
    void sendsNothingWithoutDeviceToken() {
        Member reporter = member("제보자", all(true));
        Member saver = member("토큰없음", all(true));
        Place place = place("한강공원");
        save(saver, place, true);

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(pushSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("저장한 장소 근처에 장애물 제보가 올라오면 그 장소 이름과 함께 푸시가 간다")
    void notifiesNearbyObstacleWithSavedPlaceName() {
        Member reporter = member("제보자", all(true));
        Member saver = member("근처저장", all(true));
        Place place = place("시청 앞 광장", BASE_LAT, BASE_LNG);
        save(saver, place, true);
        String token = registerDevice(saver, "token-nearby-saver");

        obstacleReportService.create(reporter.getId(), obstacleReport(BASE_LAT, BASE_LNG));

        List<PushMessage> messages = pushSender.messagesTo(token);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.SAVED_PLACE_NEARBY_OBSTACLE);
        assertThat(messages.get(0).title()).contains("시청 앞 광장 근처");
        assertThat(messages.get(0).body()).contains("계단").contains("통행 불가");
    }

    @Test
    @DisplayName("저장 장소가 반경 밖이면 장애물 푸시를 받지 않는다")
    void doesNotNotifyObstacleFarFromSavedPlace() {
        Member reporter = member("제보자", all(true));
        Member saver = member("멀리저장", all(true));
        // 약 10km 떨어진 좌표 (기본 반경 500m 밖)
        Place place = place("먼 장소", BASE_LAT + 0.1, BASE_LNG);
        save(saver, place, true);
        String token = registerDevice(saver, "token-far-saver");

        obstacleReportService.create(reporter.getId(), obstacleReport(BASE_LAT, BASE_LNG));

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 사람이 내 제보를 확인하면 제보자에게 푸시가 간다")
    void notifiesReporterWhenConfirmed() {
        Member reporter = member("제보자", all(true));
        Member confirmer = member("확인자", all(true));
        String token = registerDevice(reporter, "token-reporter-confirm");
        Long reportId = obstacleReportService
                .create(reporter.getId(), obstacleReport(BASE_LAT, BASE_LNG))
                .id();
        pushSender.clear();

        obstacleReportService.updateStatus(
                confirmer.getId(),
                reportId,
                new UpdateObstacleReportStatusRequest(UpdateObstacleReportStatusRequest.Action.STILL_PRESENT));

        List<PushMessage> messages = pushSender.messagesTo(token);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.MY_REPORT_CONFIRMED);
        assertThat(messages.get(0).body()).contains("1명");
        assertThat(messages.get(0).toFcmData()).containsEntry("id", String.valueOf(reportId));
    }

    @Test
    @DisplayName("내가 내 제보를 확인하면 나에게 푸시가 가지 않는다")
    void doesNotNotifySelfConfirmation() {
        Member reporter = member("제보자", all(true));
        String token = registerDevice(reporter, "token-self-confirm");
        Long reportId = obstacleReportService
                .create(reporter.getId(), obstacleReport(BASE_LAT, BASE_LNG))
                .id();
        pushSender.clear();

        obstacleReportService.updateStatus(
                reporter.getId(),
                reportId,
                new UpdateObstacleReportStatusRequest(UpdateObstacleReportStatusRequest.Action.STILL_PRESENT));

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("도움 요청은 반경 안에서 최근 위치를 보고한 기기에만 간다")
    void notifiesNearbyDevicesOfHelpRequest() {
        Member requester = member("요청자", all(true));
        Member nearHelper = member("가까운도우미", all(true));
        Member farHelper = member("먼도우미", all(true));

        String nearToken = registerDevice(nearHelper, "token-near", BASE_LAT, BASE_LNG);
        String farToken = registerDevice(farHelper, "token-far", BASE_LAT + 0.1, BASE_LNG);
        String requesterToken = registerDevice(requester, "token-requester", BASE_LAT, BASE_LNG);

        helpRequestService.create(requester.getId(), helpRequest());

        assertThat(pushSender.messagesTo(nearToken)).hasSize(1);
        assertThat(pushSender.messagesTo(nearToken).get(0).type()).isEqualTo(PushNotificationType.NEARBY_HELP_REQUEST);
        assertThat(pushSender.messagesTo(nearToken).get(0).body())
                .contains("시청 앞 보도")
                .contains("이동 보조");
        assertThat(pushSender.messagesTo(farToken)).isEmpty();
        assertThat(pushSender.messagesTo(requesterToken)).isEmpty();
    }

    @Test
    @DisplayName("위치를 한 번도 보고하지 않은 기기는 도움 요청 푸시를 받지 않는다")
    void skipsDevicesWithoutLocation() {
        Member requester = member("요청자", all(true));
        Member helper = member("위치없는도우미", all(true));
        String token = registerDevice(helper, "token-no-location");

        helpRequestService.create(requester.getId(), helpRequest());

        assertThat(pushSender.messagesTo(token)).isEmpty();
    }

    @Test
    @DisplayName("도움 요청을 수락하면 요청자에게 푸시가 간다")
    void notifiesRequesterWhenAccepted() {
        Member requester = member("요청자", all(true));
        Member helper = member("도우미", all(true));
        String requesterToken = registerDevice(requester, "token-requester-accept");
        var created = helpRequestService.create(requester.getId(), helpRequest());
        pushSender.clear();

        helpRequestService.accept(helper.getId(), created.id());

        List<PushMessage> messages = pushSender.messagesTo(requesterToken);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.MY_HELP_REQUEST_ACCEPTED);
        assertThat(messages.get(0).body()).contains("도우미님");
        assertThat(messages.get(0).toFcmData())
                .containsEntry(PushMessage.DATA_ROUTE, "/help/request-pending")
                .containsEntry("helpRequestId", created.id().toString());
    }

    @Test
    @DisplayName("FCM이 만료됐다고 답한 토큰은 저장소에서 지운다")
    void prunesInvalidTokens() {
        Member reporter = member("제보자", all(true));
        Member saver = member("만료토큰", all(true));
        Place place = place("만료 테스트 장소");
        save(saver, place, true);
        String token = registerDevice(saver, "token-expired");
        pushSender.reportInvalid(token);

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(deviceTokenRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("한 사람이 기기를 여러 대 쓰면 모든 기기로 간다")
    void sendsToEveryDeviceOfMember() {
        Member reporter = member("제보자", all(true));
        Member saver = member("다기기", all(true));
        Place place = place("다기기 장소");
        save(saver, place, true);
        String phone = registerDevice(saver, "token-phone");
        String tablet = registerDevice(saver, "token-tablet");

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        assertThat(pushSender.messagesTo(phone)).hasSize(1);
        assertThat(pushSender.messagesTo(tablet)).hasSize(1);
    }

    private MemberPreferences all(boolean enabled) {
        MemberPreferences preferences = MemberPreferences.empty();
        MemberPreferences.NotificationSettings settings = preferences.getNotificationSettings();
        settings.setSavedPlaceStatusChange(enabled);
        settings.setSavedPlaceNearbyObstacle(enabled);
        settings.setMyReportConfirmed(enabled);
        settings.setMyReportConfirmationRequested(enabled);
        settings.setNearbyHelpRequest(enabled);
        settings.setMyHelpRequestAccepted(enabled);
        return preferences;
    }

    private Member member(String nickname, MemberPreferences preferences) {
        return memberRepository.save(new Member(Role.USER, nickname, 15L, preferences));
    }

    private Place place(String name) {
        return place(name, BASE_LAT, BASE_LNG);
    }

    private Place place(String name, double latitude, double longitude) {
        return placeRepository.save(Place.builder()
                .externalId("push-test-" + name)
                .source("TEST")
                .name(name)
                .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)))
                .build());
    }

    private void save(Member member, Place place, boolean notificationEnabled) {
        SavedPlace savedPlace = savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());
        savedPlace.updateNotificationEnabled(notificationEnabled);
        savedPlaceRepository.save(savedPlace);
    }

    private String registerDevice(Member member, String token) {
        return registerDevice(member, token, null, null);
    }

    private String registerDevice(Member member, String token, Double latitude, Double longitude) {
        deviceTokenService.register(member.getId(), token, DevicePlatform.ANDROID, "test", latitude, longitude);
        return token;
    }

    private CreatePlaceStateReportRequest placeStateReport(Place place) {
        return new CreatePlaceStateReportRequest(
                place.getId(), PlaceAccessStatus.PARTIALLY_ACCESSIBLE, Map.of(), List.of(), null);
    }

    private CreateObstacleReportRequest obstacleReport(double latitude, double longitude) {
        return new CreateObstacleReportRequest(
                latitude,
                longitude,
                ObstacleIssueType.STAIRS,
                ObstacleSeverity.IMPASSABLE,
                Set.of(MobilityType.WHEELCHAIR),
                List.of(),
                null);
    }

    private CreateHelpRequestRequest helpRequest() {
        return new CreateHelpRequestRequest(
                null,
                "시청 앞 보도",
                BigDecimal.valueOf(BASE_LAT),
                BigDecimal.valueOf(BASE_LNG),
                null,
                null,
                Set.of(HelpKind.MOBILITY_ASSIST),
                30);
    }

    @SuppressWarnings("unused")
    private Instant unused() {
        return Instant.now();
    }
}
