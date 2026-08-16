package kr.bi.go_to.controller.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.bi.go_to.enums.OAuthProvider;

@Schema(name = "OAuthLoginRequest", description = "OAuth provider access token으로 로그인 여부를 확인하는 요청")
public record OAuthLoginRequest(
        @Schema(description = "OAuth provider", example = "KAKAO") @NotNull OAuthProvider provider,
        @Schema(description = "클라이언트가 provider에서 발급받은 access token") @NotBlank String providerAccessToken) {}
