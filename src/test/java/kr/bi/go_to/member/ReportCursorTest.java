package kr.bi.go_to.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.service.member.MyReportCursorKey;
import kr.bi.go_to.service.member.ReportCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 커서 인코딩·디코딩이 왕복하고, 깨진 값은 400으로 거절하는지 검증한다. */
class ReportCursorTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-08T01:02:03.456Z");

    @Test
    @DisplayName("분류별 위치를 담아 인코딩한 뒤 디코딩하면 같은 값이 나온다")
    void roundTripsPositions() {
        ReportCursor cursor = ReportCursor.empty()
                .with(MyReportCursorKey.OBSTACLE, new ReportCursor.Position(CREATED_AT, 12L))
                .with(MyReportCursorKey.FACILITY, new ReportCursor.Position(CREATED_AT.minusSeconds(60), 7L));

        ReportCursor decoded = ReportCursor.decode(cursor.encode());

        assertThat(decoded.get(MyReportCursorKey.OBSTACLE)).isEqualTo(new ReportCursor.Position(CREATED_AT, 12L));
        assertThat(decoded.get(MyReportCursorKey.FACILITY))
                .isEqualTo(new ReportCursor.Position(CREATED_AT.minusSeconds(60), 7L));
        // 담지 않은 분류는 "아직 읽지 않음"이라 null이다.
        assertThat(decoded.get(MyReportCursorKey.PLACE)).isNull();
    }

    @Test
    @DisplayName("커서는 내부 구조가 드러나지 않는 base64url 문자열이다")
    void encodesAsUrlSafeToken() {
        String encoded = ReportCursor.empty()
                .with(MyReportCursorKey.OBSTACLE, new ReportCursor.Position(CREATED_AT, 12L))
                .encode();

        assertThat(encoded).doesNotContain("OBSTACLE").doesNotContain(":").doesNotContain("=");
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("null·빈 문자열은 첫 페이지로 다룬다")
    void treatsBlankAsFirstPage() {
        assertThat(ReportCursor.decode(null).isEmpty()).isTrue();
        assertThat(ReportCursor.decode("").isEmpty()).isTrue();
        assertThat(ReportCursor.decode("   ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("with는 원본을 바꾸지 않고 새 커서를 돌려준다")
    void withDoesNotMutateOriginal() {
        ReportCursor original = ReportCursor.empty();

        ReportCursor next = original.with(MyReportCursorKey.PLACE, new ReportCursor.Position(CREATED_AT, 3L));

        assertThat(original.get(MyReportCursorKey.PLACE)).isNull();
        assertThat(next.get(MyReportCursorKey.PLACE)).isNotNull();
    }

    @Test
    @DisplayName("마이크로초까지 그대로 살아남는다 (밀리초로 자르면 같은 밀리초의 행이 누락된다)")
    void keepsSubMillisecondPrecision() {
        Instant precise = Instant.parse("2026-09-08T01:02:03.123456Z");

        ReportCursor decoded = ReportCursor.decode(ReportCursor.empty()
                .with(MyReportCursorKey.OBSTACLE, new ReportCursor.Position(precise, 9L))
                .encode());

        assertThat(decoded.get(MyReportCursorKey.OBSTACLE).createdAt()).isEqualTo(precise);
    }

    @Test
    @DisplayName("base64가 아니거나 형식이 깨진 커서는 400으로 거절한다")
    void rejectsMalformedCursor() {
        for (String malformed : new String[] {
            "not-base64!!",
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("OBSTACLE:only-two".getBytes()),
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("UNKNOWN:1:2:3".getBytes()),
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("OBSTACLE:not-a-number:0:2".getBytes())
        }) {
            assertThatThrownBy(() -> ReportCursor.decode(malformed))
                    .as("malformed=%s", malformed)
                    .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_REPORT_CURSOR));
        }
    }
}
