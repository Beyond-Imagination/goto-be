package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.service.push.DeviceTokenService;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushNotificationType;
import kr.bi.go_to.service.push.ReportConfirmationRequestScheduler;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 오래된 제보에 "지금도 그대로인가요?"를 묻는 배치.
 *
 * <p>등록 시각은 JPA Auditing이 현재 시각으로 채우므로, 오래된 제보를 만들려면 SQL로 시각을 뒤로 민다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PushTestConfiguration.class})
class ReportConfirmationRequestSchedulerIntegrationTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    ReportConfirmationRequestScheduler scheduler;

    @Autowired
    RecordingPushSender pushSender;

    @Autowired
    DeviceTokenService deviceTokenService;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    DeviceTokenRepository deviceTokenRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member reporter;
    String token;

    @BeforeEach
    void setUp() {
        pushSender.clear();
        deviceTokenRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        memberRepository.deleteAll();

        MemberPreferences preferences = MemberPreferences.empty();
        preferences.getNotificationSettings().setMyReportConfirmationRequested(true);
        reporter = memberRepository.save(new Member(Role.USER, "오래된제보자", 15L, preferences));

        token = "token-stale";
        deviceTokenService.register(reporter.getId(), token, DevicePlatform.ANDROID, "test", null, null);
    }

    @Test
    @DisplayName("30일 넘게 확인이 없는 제보의 제보자에게 확인 요청 푸시를 보낸다")
    void asksReporterToConfirmStaleReport() {
        Long reportId = saveReport();
        agedDays(reportId, 45);

        int sent = scheduler.requestConfirmations();

        assertThat(sent).isEqualTo(1);
        List<PushMessage> messages = pushSender.messagesTo(token);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo(PushNotificationType.MY_REPORT_CONFIRMATION_REQUESTED);
        assertThat(messages.get(0).body()).contains("계단").contains("45일");
        assertThat(messages.get(0).toFcmData()).containsEntry("id", String.valueOf(reportId));
    }

    @Test
    @DisplayName("아직 오래되지 않은 제보는 묻지 않는다")
    void skipsFreshReport() {
        Long reportId = saveReport();
        agedDays(reportId, 3);

        assertThat(scheduler.requestConfirmations()).isZero();
        assertThat(pushSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("한 번 물어본 제보는 바로 다시 묻지 않는다")
    void doesNotAskTwiceInARow() {
        Long reportId = saveReport();
        agedDays(reportId, 45);

        assertThat(scheduler.requestConfirmations()).isEqualTo(1);
        pushSender.clear();

        assertThat(scheduler.requestConfirmations()).isZero();
        assertThat(pushSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("해결된 제보는 오래돼도 묻지 않는다")
    void skipsResolvedReport() {
        Long reportId = saveReport();
        agedDays(reportId, 45);
        jdbcTemplate.update("UPDATE obstacle_reports SET status = 'RESOLVED' WHERE id = ?", reportId);

        assertThat(scheduler.requestConfirmations()).isZero();
    }

    @Test
    @DisplayName("확인 요청 알림을 꺼 둔 제보자에게는 보내지 않지만, 물어본 기록은 남겨 매일 다시 조회하지 않는다")
    void marksAskedEvenWhenNotificationIsOff() {
        MemberPreferences off = MemberPreferences.empty();
        off.getNotificationSettings().setMyReportConfirmationRequested(false);
        reporter.updatePreferences(off);
        memberRepository.save(reporter);

        Long reportId = saveReport();
        agedDays(reportId, 45);

        assertThat(scheduler.requestConfirmations()).isZero();
        assertThat(pushSender.sent()).isEmpty();

        ObstacleReport report = obstacleReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getConfirmationRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("마지막 확인이 최근이면 등록이 오래됐어도 묻지 않는다")
    void usesLastConfirmedAtAsFreshnessReference() {
        Long reportId = saveReport();
        agedDays(reportId, 90);
        jdbcTemplate.update(
                "UPDATE obstacle_reports SET last_confirmed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(2))),
                reportId);

        assertThat(scheduler.requestConfirmations()).isZero();
    }

    private Long saveReport() {
        return obstacleReportRepository
                .save(ObstacleReport.builder()
                        .reporter(reporter)
                        .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(126.9780, 37.5665)))
                        .issueType(ObstacleIssueType.STAIRS)
                        .severity(ObstacleSeverity.IMPASSABLE)
                        .affectedMobilityTypes(java.util.Set.of(MobilityType.WHEELCHAIR))
                        .photoUrls(List.of())
                        .build())
                .getId();
    }

    /** 등록 시각을 과거로 민다. Auditing이 채운 created_at은 엔티티로 바꿀 수 없다. */
    private void agedDays(Long reportId, int days) {
        jdbcTemplate.update(
                "UPDATE obstacle_reports SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(days))),
                reportId);
    }
}
