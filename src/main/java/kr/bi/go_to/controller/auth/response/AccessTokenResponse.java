package kr.bi.go_to.controller.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AccessTokenResponse", description = "액세스 토큰 갱신 응답")
public record AccessTokenResponse(
        @Schema(description = "JWT accessToken", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "accessToken 만료까지 남은 시간(초)", example = "300") long expiresIn) {}
