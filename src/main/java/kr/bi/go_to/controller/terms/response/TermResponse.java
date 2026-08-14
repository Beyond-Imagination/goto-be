package kr.bi.go_to.controller.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import kr.bi.go_to.model.terms.Term;
import kr.bi.go_to.model.terms.TermSection;

@Schema(description = "약관 상세 응답")
public record TermResponse(
        @Schema(description = "약관 식별자 키", example = "terms") String id,
        @Schema(description = "약관 비트마스크 값", example = "2") int bit,
        @Schema(description = "약관 제목", example = "서비스 이용약관") String title,
        @Schema(description = "필수 여부", example = "true") boolean required,
        @Schema(description = "현재 버전", example = "1.0.0") String version,
        @Schema(description = "시행 일자", example = "2026-08-01") LocalDate effectiveDate,
        @Schema(description = "요약 설명", example = "Beyond-Imagination 개발 동아리가 제공하는 \"함께가길\" 서비스의 이용조건 및 책임사항을 안내합니다.")
                String summary,
        @Schema(description = "조항 목록") List<TermSection> sections,
        @Schema(description = "생성 일시", example = "2026-08-14T19:00:00Z") Instant createdAt,
        @Schema(description = "수정 일시", example = "2026-08-14T19:00:00Z") Instant updatedAt) {

    public static TermResponse from(Term term) {
        return new TermResponse(
                term.getTermKey(),
                term.getBitmask(),
                term.getTitle(),
                term.isRequired(),
                term.getCurrentVersion(),
                term.getEffectiveDate(),
                term.getSummary(),
                term.getSections(),
                term.getCreatedAt(),
                term.getUpdatedAt());
    }
}
