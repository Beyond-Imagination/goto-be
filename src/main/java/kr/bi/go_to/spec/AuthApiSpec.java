package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.controller.auth.request.OAuthLoginRequest;
import kr.bi.go_to.controller.auth.request.OAuthSignupRequest;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.response.OAuthAuthenticationResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = SwaggerTag.AUTH_NAME, description = SwaggerTag.AUTH_DESCRIPTION)
public interface AuthApiSpec {
    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "OAuth 로그인",
            description = "OAuth access token을 검증하고 가입 상태에 따라 플랫폼 토큰 또는 가입 필요 상태를 반환합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "플랫폼 토큰 발급 또는 가입 필요 상태 반환",
                content = @Content(schema = @Schema(implementation = OAuthAuthenticationResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    OAuthAuthenticationResponse login(@Valid @RequestBody OAuthLoginRequest request);

    @Operation(
            tags = SwaggerTag.AUTH_NAME,
            summary = "OAuth 회원가입",
            description = "OAuth access token을 다시 검증한 뒤 사용자와 OAuth 연결을 만들고 플랫폼 토큰을 발급합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "회원가입 및 토큰 발급 성공",
                content = @Content(schema = @Schema(implementation = OAuthAuthenticationResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 또는 필수 약관 동의 검증 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 OAuth access token",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "닉네임 중복 또는 이미 완료된 OAuth 가입. 후자의 경우 로그인 화면으로 이동해 OAuth 로그인을 다시 시도합니다.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    OAuthAuthenticationResponse signup(@Valid @RequestBody OAuthSignupRequest request);

    @Operation(tags = SwaggerTag.AUTH_NAME, summary = "액세스 토큰 갱신", description = "refreshToken으로 새 accessToken을 발급합니다.")
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
