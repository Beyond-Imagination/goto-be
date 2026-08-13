package kr.bi.go_to.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import kr.bi.go_to.enums.OAuthProvider;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.service.oauth.OAuthIdentity;
import kr.bi.go_to.service.oauth.ProviderOAuthIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProviderOAuthIdentityVerifierTest {

    @Test
    void 카카오_사용자_정보가_유효하면_provider_식별자와_추천_닉네임을_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProviderOAuthIdentityVerifier verifier = new ProviderOAuthIdentityVerifier(builder);
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer provider-token"))
                .andRespond(withSuccess(
                        "{\"id\":12345,\"properties\":{\"nickname\":\"카카오닉네임\"}}", MediaType.APPLICATION_JSON));

        OAuthIdentity identity = verifier.verify(OAuthProvider.KAKAO, "provider-token");

        assertThat(identity).isEqualTo(new OAuthIdentity(OAuthProvider.KAKAO, "12345", "카카오닉네임"));
        server.verify();
    }

    @Test
    void provider가_4xx를_반환하면_유효하지_않은_OAuth_토큰으로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProviderOAuthIdentityVerifier verifier = new ProviderOAuthIdentityVerifier(builder);
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.GOOGLE, "expired-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_OAUTH_TOKEN));
        server.verify();
    }

    @Test
    void provider가_5xx를_반환하면_일시적_장애로_처리한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProviderOAuthIdentityVerifier verifier = new ProviderOAuthIdentityVerifier(builder);
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> verifier.verify(OAuthProvider.NAVER, "provider-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE));
        server.verify();
    }
}
