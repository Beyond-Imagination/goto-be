package kr.bi.go_to.service.push;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기기에 실제로 전달되는 알림 한 건.
 *
 * <p>{@code data}는 앱이 알림을 눌렀을 때 어디로 갈지 정하는 값들이다. FCM data payload는
 * 문자열만 담을 수 있어 여기서도 값은 모두 문자열로 둔다.
 */
public record PushMessage(PushNotificationType type, String title, String body, Map<String, String> data) {

    /** 알림 탭 시 이동할 앱 내 경로(expo-router path). data.route로 나간다. */
    public static final String DATA_ROUTE = "route";

    public static final String DATA_TYPE = "type";

    public PushMessage {
        data = Map.copyOf(data);
    }

    public static Builder of(PushNotificationType type, String title, String body) {
        return new Builder(type, title, body);
    }

    /** FCM에 넘길 최종 data. 종류와 경로는 항상 들어간다. */
    public Map<String, String> toFcmData() {
        Map<String, String> payload = new LinkedHashMap<>(data);
        payload.put(DATA_TYPE, type.name());
        return Map.copyOf(payload);
    }

    public static final class Builder {

        private final PushNotificationType type;
        private final String title;
        private final String body;
        private final Map<String, String> data = new LinkedHashMap<>();

        private Builder(PushNotificationType type, String title, String body) {
            this.type = type;
            this.title = title;
            this.body = body;
        }

        public Builder route(String route) {
            return data(DATA_ROUTE, route);
        }

        /** 값이 null이면 넣지 않는다. FCM data는 null 값을 허용하지 않는다. */
        public Builder data(String key, Object value) {
            if (value != null) {
                data.put(key, String.valueOf(value));
            }
            return this;
        }

        public PushMessage build() {
            return new PushMessage(type, title, body, data);
        }
    }
}
