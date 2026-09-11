package kr.bi.go_to.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 푸시 설정.
 *
 * <p>서비스 계정 키(JSON)는 yaml에 두지 않는다. Parameter Store(/goto/goto.push.credentials)에
 * base64로 넣거나, 서버에 파일로 두고 credentials-path를 준다. 둘 다 비어 있으면 푸시는
 * 비활성 상태로 동작한다(로그만 남기고 나머지 기능은 정상) — ImageStorageProperties와 같은 원칙이다.
 */
@ConfigurationProperties(prefix = "goto.push")
public class PushProperties {

    /** 서비스 계정 JSON 전체를 base64로 인코딩한 값. */
    private String credentials = "";

    /** 서비스 계정 JSON 파일 경로. credentials가 비어 있을 때만 쓴다. */
    private String credentialsPath = "";

    /** 「주변 도움 요청」 푸시를 보낼 반경(m). 도움 요청 화면의 안내 문구와 같은 값이어야 한다. */
    private int helpRequestRadiusMeters = 300;

    /** 이보다 오래된 위치를 마지막으로 보고한 기기는 반경 발송에서 제외한다. */
    private Duration locationFreshness = Duration.ofHours(6);

    /**
     * 1단계(즉시) 발송에서 "지금 그 근처에 있다"고 볼 위치의 신선도.
     *
     * <p>도움 요청은 시간이 급하지만, 아침에 거기 있었던 사람에게 점심 요청을 보내면
     * 알림만 무뎌진다. 그래서 처음엔 방금 위치를 보고한 기기에만 보내고, 수락이 없을 때만
     * locationFreshness까지 넓힌다.
     */
    private Duration immediateLocationFreshness = Duration.ofHours(1);

    /** 수락이 없을 때 확대 발송까지 기다리는 시간. */
    private Duration escalationDelay = Duration.ofMinutes(5);

    /** 저장한 장소 주변 장애물로 볼 반경(m). */
    private int savedPlaceObstacleRadiusMeters = 500;

    /** 한 번에 보낼 최대 토큰 수. FCM sendEachForMulticast 한도(500)를 넘지 않는다. */
    private int batchSize = 500;

    /** true면 FCM에 실제 전달 없이 검증만 한다(연동 점검용). */
    private boolean dryRun = false;

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public int getHelpRequestRadiusMeters() {
        return helpRequestRadiusMeters;
    }

    public void setHelpRequestRadiusMeters(int helpRequestRadiusMeters) {
        this.helpRequestRadiusMeters = helpRequestRadiusMeters;
    }

    public Duration getLocationFreshness() {
        return locationFreshness;
    }

    public void setLocationFreshness(Duration locationFreshness) {
        this.locationFreshness = locationFreshness;
    }

    public Duration getImmediateLocationFreshness() {
        return immediateLocationFreshness;
    }

    public void setImmediateLocationFreshness(Duration immediateLocationFreshness) {
        this.immediateLocationFreshness = immediateLocationFreshness;
    }

    public Duration getEscalationDelay() {
        return escalationDelay;
    }

    public void setEscalationDelay(Duration escalationDelay) {
        this.escalationDelay = escalationDelay;
    }

    public int getSavedPlaceObstacleRadiusMeters() {
        return savedPlaceObstacleRadiusMeters;
    }

    public void setSavedPlaceObstacleRadiusMeters(int savedPlaceObstacleRadiusMeters) {
        this.savedPlaceObstacleRadiusMeters = savedPlaceObstacleRadiusMeters;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /** 자격 증명이 하나라도 있으면 FCM 발송을 시도한다. */
    public boolean hasCredentials() {
        return !credentials.isBlank() || !credentialsPath.isBlank();
    }
}
