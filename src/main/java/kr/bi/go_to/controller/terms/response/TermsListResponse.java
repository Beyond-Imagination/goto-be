package kr.bi.go_to.controller.terms.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "약관 목록 응답")
public record TermsListResponse(@Schema(description = "활성화된 약관 목록") List<TermResponse> terms) {}
