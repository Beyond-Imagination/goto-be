package kr.bi.go_to.controller.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse", description = "임시 로그인 응답")
public record LoginResponse(
        @Schema(description = "JWT accessToken", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "JWT refreshToken", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "accessToken 만료까지 남은 시간(초)", example = "300")
        long expiresIn
) {
}
