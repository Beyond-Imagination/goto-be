package kr.bi.go_to.model.terms;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "약관 조항")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TermSection(
        @Schema(description = "조항 제목", example = "제 1 조 (목적)") String title,
        @Schema(description = "조항 본문 내용", example = "본 약관은...") String content,
        @Schema(description = "조항 세부 항목 리스트") List<String> items) {

    public TermSection(String title, String content) {
        this(title, content, null);
    }
}
