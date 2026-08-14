package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.bi.go_to.controller.terms.response.TermHistoryResponse;
import kr.bi.go_to.controller.terms.response.TermResponse;
import kr.bi.go_to.controller.terms.response.TermsListResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = SwaggerTag.AUTH_NAME, description = SwaggerTag.AUTH_DESCRIPTION)
public interface TermsApiSpec {

    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "활성화된 전체 약관 목록 조회",
            description = "회원가입 약관 동의 화면 및 설정 메뉴에서 표시할 활성 약관 목록과 전문을 조회합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "약관 목록 반환",
                content = @Content(schema = @Schema(implementation = TermsListResponse.class)))
    })
    TermsListResponse getTerms();

    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "특정 단일 약관 조회",
            description = "지정한 약관 키(termId)에 해당하는 활성 약관 상세 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "약관 상세 반환",
                content = @Content(schema = @Schema(implementation = TermResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "약관을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    TermResponse getTerm(@PathVariable String termId);

    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "특정 약관 개정 이력 목록 조회",
            description = "지정한 약관 키(termId)에 해당하는 모든 버전별 개정 이력을 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "약관 개정 이력 목록 반환"),
        @ApiResponse(
                responseCode = "404",
                description = "약관을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    List<TermHistoryResponse> getTermHistories(@PathVariable String termId);
}
