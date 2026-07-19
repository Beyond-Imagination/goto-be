package kr.bi.go_to.controller.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest", description = "액세스 토큰 갱신 요청")
public record RefreshRequest(
        @Schema(description = "로그인 시 발급된 refreshToken", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank
        String refreshToken
) {
}
