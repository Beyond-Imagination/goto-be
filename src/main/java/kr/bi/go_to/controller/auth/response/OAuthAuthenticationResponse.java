package kr.bi.go_to.controller.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.bi.go_to.enums.OAuthProvider;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "OAuthAuthenticationResponse", description = "OAuth 로그인 결과")
public record OAuthAuthenticationResponse(
        @Schema(description = "인증 결과 상태", example = "AUTHENTICATED") OAuthAuthenticationStatus status,
        @Schema(description = "플랫폼 JWT access token") String accessToken,
        @Schema(description = "플랫폼 JWT refresh token") String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "access token 만료까지 남은 시간(초)", example = "300") Long expiresIn,
        @Schema(description = "가입이 필요한 OAuth provider", example = "KAKAO") OAuthProvider provider,
        @Schema(description = "provider 프로필에서 가져온 닉네임 추천값", example = "함께가는길") String suggestedNickname) {

    public static OAuthAuthenticationResponse authenticated(String accessToken, String refreshToken, long expiresIn) {
        return new OAuthAuthenticationResponse(
                OAuthAuthenticationStatus.AUTHENTICATED, accessToken, refreshToken, "Bearer", expiresIn, null, null);
    }

    public static OAuthAuthenticationResponse signUpRequired(OAuthProvider provider, String suggestedNickname) {
        return new OAuthAuthenticationResponse(
                OAuthAuthenticationStatus.SIGN_UP_REQUIRED, null, null, null, null, provider, suggestedNickname);
    }
}
