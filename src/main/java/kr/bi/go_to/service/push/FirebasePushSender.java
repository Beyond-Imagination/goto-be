package kr.bi.go_to.service.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.properties.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FCM HTTP v1로 보내는 실제 발송 채널.
 *
 * <p>안드로이드와 iOS 모두 같은 FCM 토큰으로 보낸다(iOS는 Firebase 콘솔에 APNs 키가 올라가 있어야
 * FCM이 APNs로 중계한다). 알림 표시는 서버가 담당하고, 앱은 data에 담긴 route로 화면만 연다.
 */
public class FirebasePushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushSender.class);

    /** 앱이 만들어 두는 안드로이드 알림 채널 id. FE의 expo-notifications 채널 설정과 같아야 한다. */
    public static final String ANDROID_CHANNEL_ID = "goto-default";

    private final FirebaseMessaging messaging;
    private final PushProperties properties;

    public FirebasePushSender(FirebaseMessaging messaging, PushProperties properties) {
        this.messaging = messaging;
        this.properties = properties;
    }

    @Override
    public PushSendResult send(List<DeviceToken> targets, PushMessage message) {
        if (targets.isEmpty()) {
            return PushSendResult.none();
        }

        int successCount = 0;
        List<String> invalidTokens = new ArrayList<>();

        for (int start = 0; start < targets.size(); start += properties.getBatchSize()) {
            List<DeviceToken> batch =
                    targets.subList(start, Math.min(start + properties.getBatchSize(), targets.size()));
            List<Message> messages =
                    batch.stream().map(target -> toMessage(target, message)).toList();

            try {
                BatchResponse response = messaging.sendEach(messages, properties.isDryRun());
                successCount += response.getSuccessCount();
                invalidTokens.addAll(collectInvalidTokens(batch, response));
            } catch (FirebaseMessagingException exception) {
                // 한 배치가 실패해도 나머지 배치는 계속 시도한다. 푸시 실패가 본래 요청을 깨뜨리면 안 된다.
                log.warn("FCM 발송 실패 (type={}, tokens={})", message.type(), batch.size(), exception);
            }
        }

        return new PushSendResult(successCount, invalidTokens);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private Message toMessage(DeviceToken target, PushMessage message) {
        Map<String, String> data = message.toFcmData();

        return Message.builder()
                .setToken(target.getToken())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL_ID)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        // iOS는 앱이 꺼져 있어도 알림을 띄우도록 alert + sound를 명시한다.
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();
    }

    /**
     * 다시 쓸 수 없는 토큰만 골라낸다.
     *
     * <p>UNREGISTERED는 앱 삭제·재설치, INVALID_ARGUMENT는 손상된 토큰이다. 그 외(할당량 초과,
     * 서버 일시 오류)는 토큰 잘못이 아니므로 지우면 안 된다.
     */
    private List<String> collectInvalidTokens(List<DeviceToken> batch, BatchResponse response) {
        List<String> invalid = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();

        for (int index = 0; index < responses.size(); index++) {
            SendResponse each = responses.get(index);
            if (each.isSuccessful() || each.getException() == null) {
                continue;
            }

            FirebaseMessagingException exception = each.getException();
            boolean gone = exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || exception.getErrorCode() == ErrorCode.INVALID_ARGUMENT;

            if (gone) {
                invalid.add(batch.get(index).getToken());
            } else {
                // 원인 메시지가 없으면 "왜 안 갔는지"를 추적할 수 없다. 토큰은 남기지 않는다.
                log.warn(
                        "FCM 개별 발송 실패 (errorCode={}, messagingErrorCode={}, message={})",
                        exception.getErrorCode(),
                        exception.getMessagingErrorCode(),
                        exception.getMessage(),
                        exception);
            }
        }

        return invalid;
    }
}
