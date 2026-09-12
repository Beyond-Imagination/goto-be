package kr.bi.go_to.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushNotificationType;
import kr.bi.go_to.service.push.PushSendResult;
import kr.bi.go_to.service.push.PushSender;

/**
 * 테스트용 발송 채널.
 *
 * <p>FCM에 실제로 보내는 대신 "누구에게 무엇을 보냈는지"를 기록한다. 특정 토큰을 만료된 것으로
 * 취급하도록 지정할 수 있어, 죽은 토큰 정리 동작도 검증할 수 있다.
 */
public class RecordingPushSender implements PushSender {

    public record Sent(PushMessage message, List<String> tokens) {

        public PushNotificationType type() {
            return message.type();
        }
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final List<String> tokensToReportInvalid = new CopyOnWriteArrayList<>();

    @Override
    public PushSendResult send(List<DeviceToken> targets, PushMessage message) {
        List<String> tokens = targets.stream().map(DeviceToken::getToken).toList();
        sent.add(new Sent(message, tokens));

        List<String> invalid = new ArrayList<>(tokens);
        invalid.retainAll(tokensToReportInvalid);

        return new PushSendResult(tokens.size() - invalid.size(), invalid);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public void reportInvalid(String token) {
        tokensToReportInvalid.add(token);
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public List<Sent> sentOf(PushNotificationType type) {
        return sent.stream().filter(each -> each.type() == type).toList();
    }

    /** 이 토큰으로 나간 알림들. */
    public List<PushMessage> messagesTo(String token) {
        return sent.stream()
                .filter(each -> each.tokens().contains(token))
                .map(Sent::message)
                .toList();
    }

    public void clear() {
        sent.clear();
        tokensToReportInvalid.clear();
    }
}
