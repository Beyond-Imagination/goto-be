package kr.bi.go_to.service.member;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;

/**
 * 내 제보 기록·확인 기록 목록의 커서.
 *
 * <p>내 제보 기록은 서로 다른 세 테이블(장애물·장소·시설)을 합쳐서 최신순으로 보여준다. 테이블이
 * 다르면 id를 서로 비교할 수 없으므로, 분류별로 "어디까지 읽었는지"를 각각 들고 다닌다. 각 분류
 * 안에서는 (created_at, id)가 유일하므로 그 조합으로 정확히 이어 읽을 수 있다.
 *
 * <p>인코딩은 `OBSTACLE:1757300000:123456000:12`(분류:초:나노:id)를 base64url로 감싼 형태이며,
 * 클라이언트는 내부 구조를 몰라도 되고 값을 그대로 다음 요청에 돌려주면 된다.
 *
 * <p>시각을 밀리초로 줄이면 안 된다. Postgres는 timestamptz를 마이크로초까지 저장하는데,
 * 커서를 밀리초로 잘라 버리면 같은 밀리초 안의 행이 `created_at < 커서`에도 `= 커서`에도 걸리지
 * 않아 조용히 누락된다(연달아 저장된 제보에서 실제로 재현됨). 그래서 초와 나노를 따로 담는다.
 */
public final class ReportCursor {

    private static final String ENTRY_DELIMITER = ",";
    private static final String FIELD_DELIMITER = ":";

    private final Map<MyReportCursorKey, Position> positions;

    /** 목록의 한 분류에서 마지막으로 읽은 항목. */
    public record Position(Instant createdAt, long id) {}

    private ReportCursor(Map<MyReportCursorKey, Position> positions) {
        this.positions = positions;
    }

    public static ReportCursor empty() {
        return new ReportCursor(new EnumMap<>(MyReportCursorKey.class));
    }

    /** 빈 값·null은 "첫 페이지"로 다룬다. 형식이 깨진 값은 400으로 거절한다. */
    public static ReportCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }

        Map<MyReportCursorKey, Position> positions = new EnumMap<>(MyReportCursorKey.class);
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            for (String entry : decoded.split(ENTRY_DELIMITER)) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] fields = entry.split(FIELD_DELIMITER);
                if (fields.length != 4) {
                    throw new BusinessException(ErrorCode.INVALID_REPORT_CURSOR);
                }
                positions.put(
                        MyReportCursorKey.valueOf(fields[0]),
                        new Position(
                                Instant.ofEpochSecond(Long.parseLong(fields[1]), Long.parseLong(fields[2])),
                                Long.parseLong(fields[3])));
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_CURSOR);
        }

        return new ReportCursor(positions);
    }

    public String encode() {
        StringBuilder builder = new StringBuilder();
        positions.forEach((key, position) -> {
            if (!builder.isEmpty()) {
                builder.append(ENTRY_DELIMITER);
            }
            builder.append(key.name())
                    .append(FIELD_DELIMITER)
                    .append(position.createdAt().getEpochSecond())
                    .append(FIELD_DELIMITER)
                    .append(position.createdAt().getNano())
                    .append(FIELD_DELIMITER)
                    .append(position.id());
        });

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 해당 분류를 아직 읽지 않았으면 null. */
    public Position get(MyReportCursorKey key) {
        return positions.get(key);
    }

    public ReportCursor with(MyReportCursorKey key, Position position) {
        Map<MyReportCursorKey, Position> next = new EnumMap<>(positions);
        next.put(key, position);
        return new ReportCursor(next);
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
