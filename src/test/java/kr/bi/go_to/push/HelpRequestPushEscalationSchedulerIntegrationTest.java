package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.bi.go_to.controller.help.request.CreateHelpRequestRequest;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.help.HelpKind;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.NotificationRepository;
import kr.bi.go_to.service.HelpRequestService;
import kr.bi.go_to.service.push.DeviceTokenService;
import kr.bi.go_to.service.push.HelpRequestPushEscalationScheduler;
import kr.bi.go_to.service.push.PushNotificationType;
import kr.bi.go_to.support.PushTestConfiguration;
import kr.bi.go_to.support.RecordingPushSender;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 주변 도움 요청 푸시의 2단계 확대 발송.
 *
 * <p>1단계는 "방금 위치를 보고한 기기", 확대는 "조금 오래된 위치까지"다. 이 경계와 중복 방지가
 * 이 기능의 전부라, 위치 시각·요청 시각을 SQL로 밀어 경계 양쪽을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PushTestConfiguration.class})
class HelpRequestPushEscalationSchedulerIntegrationTest {

    private static final double BASE_LAT = 37.5665;
    private static final double BASE_LNG = 126.978;

    @Autowired
    HelpRequestPushEscalationScheduler scheduler;

    @Autowired
    HelpRequestService helpRequestService;

    @Autowired
    DeviceTokenService deviceTokenService;

    @Autowired
    RecordingPushSender pushSender;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    DeviceTokenRepository deviceTokenRepository;

    @Autowired
    HelpRequestRepository helpRequestRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member requester;

    @BeforeEach
    void setUp() {
        pushSender.clear();
        notificationRepository.deleteAll();
        deviceTokenRepository.deleteAll();
        helpRequestRepository.deleteAll();
        memberRepository.deleteAll();

        requester = member("요청자");
    }

    @Test
    @DisplayName("1단계에서는 방금 위치를 보고한 기기에만 보낸다")
    void immediateSendTargetsOnlyFreshLocations() {
        String fresh = registerDevice(member("방금 있던 사람"), "token-fresh");
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);

        helpRequestService.create(requester.getId(), helpRequest());

        assertThat(pushSender.messagesTo(fresh)).hasSize(1);
        assertThat(pushSender.messagesTo(stale)).isEmpty();
    }

    @Test
    @DisplayName("5분 안에 수락이 없으면 위치가 조금 오래된 사람까지 넓혀 다시 보낸다")
    void escalatesToStaleLocationsWhenNobodyAccepts() {
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);
        UUID helpRequestId = createAndAge(6);

        int sent = scheduler.escalate();

        assertThat(sent).isEqualTo(1);
        List<kr.bi.go_to.service.push.PushMessage> messages = pushSender.messagesTo(stale);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.NEARBY_HELP_REQUEST);
        assertThat(messages.get(0).toFcmData()).containsEntry("helpRequestId", helpRequestId.toString());
    }

    @Test
    @DisplayName("1단계에서 이미 받은 사람에게는 확대 발송이 다시 가지 않는다")
    void doesNotNotifyTwice() {
        String fresh = registerDevice(member("방금 있던 사람"), "token-fresh");
        createAndAge(6);
        pushSender.clear();

        scheduler.escalate();

        assertThat(pushSender.messagesTo(fresh)).isEmpty();
    }

    @Test
    @DisplayName("확대 발송에서도 6시간을 넘긴 위치는 제외한다")
    void keepsOuterFreshnessBoundary() {
        String tooOld = registerDevice(member("어제 사람"), "token-too-old");
        agedLocationHours(tooOld, 7);
        createAndAge(6);
        pushSender.clear();

        scheduler.escalate();

        assertThat(pushSender.messagesTo(tooOld)).isEmpty();
    }

    @Test
    @DisplayName("아직 대기 시간이 지나지 않은 요청은 확대하지 않는다")
    void waitsForEscalationDelay() {
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);
        createAndAge(2);

        assertThat(scheduler.escalate()).isZero();
        assertThat(pushSender.messagesTo(stale)).isEmpty();
    }

    @Test
    @DisplayName("누군가 수락한 요청은 확대하지 않는다")
    void skipsAcceptedRequests() {
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);
        UUID helpRequestId = createAndAge(6);
        helpRequestService.accept(member("도우미").getId(), helpRequestId);
        pushSender.clear();

        assertThat(scheduler.escalate()).isZero();
        assertThat(pushSender.messagesTo(stale)).isEmpty();
    }

    @Test
    @DisplayName("이미 만료된 요청은 확대하지 않는다")
    void skipsExpiredRequests() {
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);
        UUID helpRequestId = createAndAge(6);
        jdbcTemplate.update(
                "UPDATE help_requests SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))),
                helpRequestId);

        assertThat(scheduler.escalate()).isZero();
    }

    @Test
    @DisplayName("확대는 요청당 한 번뿐이다 — 매분 같은 요청을 다시 보내지 않는다")
    void escalatesOnlyOnce() {
        String stale = registerDevice(member("3시간 전 사람"), "token-stale");
        agedLocationHours(stale, 3);
        UUID helpRequestId = createAndAge(6);

        assertThat(scheduler.escalate()).isEqualTo(1);
        pushSender.clear();

        assertThat(scheduler.escalate()).isZero();
        assertThat(pushSender.sent()).isEmpty();
        assertThat(helpRequestRepository.findById(helpRequestId).orElseThrow().getPushEscalatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("보낼 대상이 없어도 확대한 것으로 기록해 반복 조회를 멈춘다")
    void marksEscalatedEvenWithoutTargets() {
        UUID helpRequestId = createAndAge(6);

        assertThat(scheduler.escalate()).isZero();
        assertThat(helpRequestRepository.findById(helpRequestId).orElseThrow().getPushEscalatedAt())
                .isNotNull();
    }

    private Member member(String nickname) {
        MemberPreferences preferences = MemberPreferences.empty();
        return memberRepository.save(new Member(Role.USER, nickname, 15L, preferences));
    }

    private String registerDevice(Member member, String token) {
        deviceTokenService.register(member.getId(), token, DevicePlatform.ANDROID, "test", BASE_LAT, BASE_LNG);
        return token;
    }

    /** 요청을 만들고 올라온 지 지정한 분만큼 지난 것으로 민다. */
    private UUID createAndAge(int minutesAgo) {
        UUID id = helpRequestService.create(requester.getId(), helpRequest()).id();
        jdbcTemplate.update(
                "UPDATE help_requests SET requested_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(minutesAgo))),
                id);
        return id;
    }

    private void agedLocationHours(String token, int hours) {
        jdbcTemplate.update(
                "UPDATE device_tokens SET last_location_at = ? WHERE token = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(hours))),
                token);
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
}
