package kr.bi.go_to.support;

import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.service.oauth.OAuthIdentity;
import kr.bi.go_to.service.oauth.OAuthIdentityVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class OAuthIdentityTestConfiguration {

    @Bean
    @Primary
    OAuthIdentityVerifier oauthIdentityVerifier() {
        return (provider, providerAccessToken) -> {
            if ("invalid-token".equals(providerAccessToken)) {
                throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
            }
            return new OAuthIdentity(provider, provider.name().toLowerCase() + "-" + providerAccessToken, "추천닉네임");
        };
    }
}
