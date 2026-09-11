package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.properties.PushProperties;
import kr.bi.go_to.service.push.FirebasePushSender;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushMessages;
import kr.bi.go_to.service.push.PushSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 기기로 푸시가 도착하는지 확인하는 수동 테스트.
 *
 * <p>운영과 같은 경로(PushMessages로 문구를 만들고 FirebasePushSender로 발송)를 그대로 태우므로,
 * 통과하면 "서버가 만든 메시지를 FCM이 받아 그 기기로 보냈다"까지 검증된다. 실제로 화면에 떴는지는
 * 기기에서 눈으로 확인해야 한다.
 *
 * <p>환경 변수가 없으면 자동으로 건너뛴다(일반 테스트 실행에 영향 없음). goto-be/.env에 넣어도 된다:
 *
 * <pre>
 * GOTO_PUSH_E2E=true
 * GOTO_PUSH_TEST_TOKEN=기기에서 찍힌 FCM 토큰
 * GOTO_PUSH_TEST_CREDENTIALS_PATH=/path/to/service-account.json   (또는 GOTO_PUSH_TEST_CREDENTIALS=base64)
 * </pre>
 *
 * <p>플래그를 따로 둔 이유: 토큰만 있으면 자동으로 도는 구조면 평소 {@code ./gradlew test}가 실제
 * 기기로 푸시를 쏴 버린다. 보내고 싶을 때만 켠다.
 *
 * <pre>
 * ./gradlew test --tests '*PushDeliveryManualE2ETest*'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GOTO_PUSH_E2E", matches = "(?i)true")
class PushDeliveryManualE2ETest {

    private static final String FIREBASE_APP_NAME = "goto-push-manual-test";

    @Test
    @DisplayName("저장 장소 상태 변경 알림이 실제 기기로 발송된다")
    void sendsSavedPlaceNotification() throws Exception {
        PushSendResult result =
                send(PushMessages.savedPlaceStateReported(1L, "국립중앙박물관", PlaceAccessStatus.PARTIALLY_ACCESSIBLE));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    @DisplayName("주변 장애물 알림이 실제 기기로 발송된다 — 누르면 제보 상세로 열려야 한다")
    void sendsNearbyObstacleNotification() throws Exception {
        PushSendResult result = send(PushMessages.savedPlaceNearbyObstacle(
                1247L, "국립중앙박물관", ObstacleIssueType.SIDEWALK_DAMAGE, ObstacleSeverity.CAUTION));

        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("도움 요청 수락 알림이 실제 기기로 발송된다")
    void sendsHelpRequestAcceptedNotification() throws Exception {
        PushSendResult result = send(PushMessages.myHelpRequestAccepted(UUID.randomUUID(), "도우미"));

        assertThat(result.successCount()).isEqualTo(1);
    }

    private PushSendResult send(PushMessage message) throws Exception {
        PushProperties properties = new PushProperties();
        FirebasePushSender sender = new FirebasePushSender(messaging(), properties);

        return sender.send(List.of(deviceToken()), message);
    }

    private FirebaseMessaging messaging() throws Exception {
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(existing -> FIREBASE_APP_NAME.equals(existing.getName()))
                .findFirst()
                .orElseGet(() -> {
                    try (InputStream credentials = openCredentials()) {
                        return FirebaseApp.initializeApp(
                                FirebaseOptions.builder()
                                        .setCredentials(GoogleCredentials.fromStream(credentials))
                                        .build(),
                                FIREBASE_APP_NAME);
                    } catch (Exception exception) {
                        throw new IllegalStateException(
                                "Firebase 자격 증명을 읽지 못했습니다. GOTO_PUSH_TEST_CREDENTIALS_PATH 또는 GOTO_PUSH_TEST_CREDENTIALS를 확인하세요.",
                                exception);
                    }
                });

        return FirebaseMessaging.getInstance(app);
    }

    private static InputStream openCredentials() throws Exception {
        String path = System.getenv("GOTO_PUSH_TEST_CREDENTIALS_PATH");
        if (path != null && !path.isBlank()) {
            return Files.newInputStream(Path.of(path));
        }

        String encoded = System.getenv("GOTO_PUSH_TEST_CREDENTIALS");
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "GOTO_PUSH_TEST_CREDENTIALS_PATH(파일 경로) 또는 GOTO_PUSH_TEST_CREDENTIALS(base64) 중 하나가 필요합니다.");
        }

        String trimmed = encoded.trim();
        byte[] json = trimmed.startsWith("{")
                ? trimmed.getBytes(StandardCharsets.UTF_8)
                : Base64.getDecoder().decode(trimmed.replaceAll("\\s", ""));
        return new ByteArrayInputStream(json);
    }

    /** DB를 쓰지 않는다. FirebasePushSender는 토큰 문자열만 읽는다. */
    private DeviceToken deviceToken() {
        return new DeviceToken(
                new Member(Role.USER, "push-e2e"),
                System.getenv("GOTO_PUSH_TEST_TOKEN").trim(),
                DevicePlatform.ANDROID,
                "manual-test",
                Instant.now());
    }
}
