package kr.bi.go_to.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import kr.bi.go_to.properties.PushProperties;
import kr.bi.go_to.service.push.FirebasePushSender;
import kr.bi.go_to.service.push.LoggingPushSender;
import kr.bi.go_to.service.push.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 푸시 발송 채널 구성.
 *
 * <p>자격 증명이 있으면 FCM으로, 없으면 로그만 남기는 채널로 띄운다. 자격 증명이 잘못돼도
 * 애플리케이션 기동은 막지 않는다 — 푸시 하나 때문에 서버 전체가 뜨지 않는 편이 더 나쁘다.
 */
@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /** 여러 인스턴스에서 초기화해도 충돌하지 않도록 이름을 고정한다. */
    private static final String FIREBASE_APP_NAME = "goto-push";

    @Bean
    public PushSender pushSender(PushProperties properties) {
        if (!properties.hasCredentials()) {
            log.info("goto.push 자격 증명이 없어 푸시를 비활성 상태로 시작합니다.");
            return new LoggingPushSender();
        }

        try (InputStream credentials = openCredentials(properties)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> FIREBASE_APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, FIREBASE_APP_NAME));

            log.info("FCM 푸시를 활성화했습니다. (dryRun={})", properties.isDryRun());
            return new FirebasePushSender(FirebaseMessaging.getInstance(app), properties);
        } catch (IOException | RuntimeException exception) {
            log.error("Firebase 자격 증명을 읽지 못해 푸시를 비활성 상태로 시작합니다.", exception);
            return new LoggingPushSender();
        }
    }

    private InputStream openCredentials(PushProperties properties) throws IOException {
        if (!properties.getCredentials().isBlank()) {
            return new ByteArrayInputStream(decode(properties.getCredentials()));
        }
        return Files.newInputStream(Path.of(properties.getCredentialsPath()));
    }

    /**
     * Parameter Store에는 base64로 넣는 것을 기본으로 하되, 실수로 JSON 원문을 넣은 경우도 받아준다.
     * (줄바꿈이 많은 서비스 계정 키는 base64가 안전하다.)
     */
    private byte[] decode(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{")) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
        return Base64.getDecoder().decode(trimmed.replaceAll("\\s", ""));
    }
}
