package kr.bi.go_to.controller.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.bi.go_to.enums.OAuthProvider;
import kr.bi.go_to.model.member.MemberPreferences;

@Schema(name = "OAuthSignupRequest", description = "OAuth 인증을 다시 검증한 뒤 서비스를 가입하는 요청")
public record OAuthSignupRequest(
        @Schema(description = "OAuth provider", example = "KAKAO") @NotNull OAuthProvider provider,
        @Schema(description = "클라이언트가 provider에서 발급받은 access token") @NotBlank String providerAccessToken,
        @Schema(description = "사용자가 확정한 닉네임", example = "함께가는길") @NotBlank String nickname,
        @Schema(description = "약관 동의 비트마스크", example = "15") @NotNull Long agreementMask,
        @Schema(description = "사용자 개인화 설정") @NotNull @Valid MemberPreferences preferences) {}
