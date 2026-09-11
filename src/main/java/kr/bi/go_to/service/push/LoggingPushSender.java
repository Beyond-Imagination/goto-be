package kr.bi.go_to.service.push;

import java.util.List;
import kr.bi.go_to.model.push.DeviceToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Firebase 자격 증명이 없을 때 쓰는 발송 채널.
 *
 * <p>보내지 않고 로그만 남긴다. 로컬 개발과 테스트에서 푸시 설정 없이도 제보·도움 요청 흐름을
 * 그대로 돌리기 위한 것이며, 토큰을 무효로 판단하지 않는다.
 */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public PushSendResult send(List<DeviceToken> targets, PushMessage message) {
        log.info(
                "푸시 비활성 상태 - 발송하지 않음 (type={}, targets={}, title={})", message.type(), targets.size(), message.title());
        return PushSendResult.none();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
