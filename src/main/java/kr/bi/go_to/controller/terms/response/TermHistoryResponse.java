package kr.bi.go_to.controller.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import kr.bi.go_to.model.terms.TermHistory;
import kr.bi.go_to.model.terms.TermSection;

@Schema(description = "약관 개정 이력 응답")
public record TermHistoryResponse(
        @Schema(description = "개정 이력 식별자", example = "1") Long id,
        @Schema(description = "약관 버전", example = "1.0.0") String version,
        @Schema(description = "시행 일자", example = "2026-08-01") LocalDate effectiveDate,
        @Schema(description = "요약 설명") String summary,
        @Schema(description = "조항 목록") List<TermSection> sections,
        @Schema(description = "개정 사유 및 변경점", example = "최초 제정") String changeLog,
        @Schema(description = "개정 일시") Instant createdAt,
        @Schema(description = "개정자", example = "SYSTEM") String createdBy) {

    public static TermHistoryResponse from(TermHistory history) {
        return new TermHistoryResponse(
                history.getId(),
                history.getVersion(),
                history.getEffectiveDate(),
                history.getSummary(),
                history.getSections(),
                history.getChangeLog(),
                history.getCreatedAt(),
                history.getCreatedBy());
    }
}
