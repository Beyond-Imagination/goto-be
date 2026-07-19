package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.controller.auth.request.LoginRequest;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.response.LoginResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = SwaggerTag.AUTH_NAME, description = SwaggerTag.AUTH_DESCRIPTION)
public interface AuthApiSpec {
    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "임시 로그인",
            description = "임시 계정 정보로 accessToken과 refreshToken을 발급합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "토큰 발급 성공",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    LoginResponse login(@Valid @RequestBody LoginRequest request);

    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "임시 액세스 토큰 갱신",
            description = "refreshToken으로 새 accessToken을 발급합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "액세스 토큰 갱신 성공",
                content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 리프레시 토큰",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request);
}
