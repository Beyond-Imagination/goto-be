package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.help.request.CreateHelpRequestRequest;
import kr.bi.go_to.controller.help.response.HelpRequestResponse;
import kr.bi.go_to.controller.help.response.NearbyHelpRequestResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = SwaggerTag.HELP_REQUEST_NAME, description = SwaggerTag.HELP_REQUEST_DESCRIPTION)
public interface HelpRequestApiSpec {

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "도움 요청 생성")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "도움 요청 생성 성공",
                content = @Content(schema = @Schema(implementation = HelpRequestResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    HelpRequestResponse create(AuthenticatedMember member, @Valid @RequestBody CreateHelpRequestRequest request);

    @Operation(
            tags = SwaggerTag.HELP_REQUEST_NAME,
            summary = "주변 도움 요청 조회",
            description = "수락 전에는 요청자의 정확 좌표와 개인정보를 노출하지 않는 주변 요청 목록을 반환합니다.")
    List<NearbyHelpRequestResponse> findNearby(
            AuthenticatedMember member,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @RequestParam @Min(1) @Max(5000) int radiusMeters);

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "내 도움 요청 조회")
    List<HelpRequestResponse> findMine(AuthenticatedMember member);

    @Operation(
            tags = SwaggerTag.HELP_REQUEST_NAME,
            summary = "도움 요청 상세 조회",
            description = "요청자 또는 수락한 도우미만 정확 위치를 포함한 상세 정보를 조회합니다.")
    HelpRequestResponse get(AuthenticatedMember member, UUID id);

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "도움 요청 수락")
    HelpRequestResponse accept(AuthenticatedMember member, UUID id);

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "도움 요청 거절")
    @ApiResponse(responseCode = "204", description = "거절 처리 성공")
    void reject(AuthenticatedMember member, UUID id);

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "도움 완료")
    HelpRequestResponse complete(AuthenticatedMember member, UUID id);

    @Operation(tags = SwaggerTag.HELP_REQUEST_NAME, summary = "도움 요청 취소")
    HelpRequestResponse cancel(AuthenticatedMember member, UUID id);
}
