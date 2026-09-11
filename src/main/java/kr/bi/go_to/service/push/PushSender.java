package kr.bi.go_to.service.push;

import java.util.List;
import kr.bi.go_to.model.push.DeviceToken;

/**
 * 푸시 전송 채널.
 *
 * <p>운영에서는 FCM({@code FirebasePushSender})을 쓰고, 자격 증명이 없는 환경(로컬·테스트)에서는
 * 로그만 남기는 구현이 대신 들어간다. 덕분에 Firebase 설정 없이도 나머지 기능이 그대로 돈다.
 */
public interface PushSender {

    PushSendResult send(List<DeviceToken> targets, PushMessage message);

    /** 실제로 발송되는 채널인지. 관리자·헬스체크 용도로만 쓴다. */
    boolean isEnabled();
}
