package kr.bi.go_to.service.push;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;

/**
 * 알림 목록의 커서.
 *
 * <p>한 테이블만 읽으므로 (created_at, id) 한 쌍이면 충분하다. 인코딩은
 * `1757300000:123456000:12`(초:나노:id)를 base64url로 감싼 형태다.
 *
 * <p>시각을 밀리초로 줄이면 안 된다 — Postgres는 마이크로초까지 저장해서, 같은 밀리초 안의
 * 행이 조용히 누락된다(ReportCursor에서 실제로 겪은 문제다). 그래서 초와 나노를 따로 담는다.
 */
public record NotificationCursor(Instant createdAt, long id) {

    private static final String DELIMITER = ":";

    /** 빈 값·null은 "첫 페이지"(커서 없음)로 다룬다. 형식이 깨진 값은 400으로 거절한다. */
    public static NotificationCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = decoded.split(DELIMITER);
            if (fields.length != 3) {
                throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_CURSOR);
            }

            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(fields[0]), Long.parseLong(fields[1]));
            return new NotificationCursor(createdAt, Long.parseLong(fields[2]));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_CURSOR);
        }
    }

    public String encode() {
        String raw = createdAt.getEpochSecond() + DELIMITER + createdAt.getNano() + DELIMITER + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
