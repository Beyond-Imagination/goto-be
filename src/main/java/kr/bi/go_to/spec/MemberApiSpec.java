package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.controller.member.response.NicknameAvailabilityResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = SwaggerTag.AUTH_NAME, description = SwaggerTag.AUTH_DESCRIPTION)
public interface MemberApiSpec {
    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "닉네임 사용 가능 여부 조회",
            description = "닉네임은 예약되지 않으며, 최종 OAuth 회원가입 시 유니크 제약으로 다시 검증됩니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "닉네임 사용 가능 여부 반환",
                content = @Content(schema = @Schema(implementation = NicknameAvailabilityResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "닉네임 형식 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    NicknameAvailabilityResponse checkNicknameAvailability(@PathVariable String nickname);
}
