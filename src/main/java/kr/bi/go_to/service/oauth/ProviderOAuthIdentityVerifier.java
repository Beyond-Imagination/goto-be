package kr.bi.go_to.service.oauth;

import kr.bi.go_to.enums.OAuthProvider;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class ProviderOAuthIdentityVerifier implements OAuthIdentityVerifier {

    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String GOOGLE_USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final RestClient restClient;

    public ProviderOAuthIdentityVerifier(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthIdentity verify(OAuthProvider provider, String providerAccessToken) {
        return switch (provider) {
            case NAVER -> naverIdentity(providerAccessToken);
            case KAKAO -> kakaoIdentity(providerAccessToken);
            case GOOGLE -> googleIdentity(providerAccessToken);
        };
    }

    private OAuthIdentity naverIdentity(String providerAccessToken) {
        JsonNode response = getUserInfo(NAVER_USER_INFO_URL, providerAccessToken);
        return new OAuthIdentity(
                OAuthProvider.NAVER,
                requiredText(response, "/response/id"),
                optionalText(response, "/response/nickname", "/response/name"));
    }

    private OAuthIdentity kakaoIdentity(String providerAccessToken) {
        JsonNode response = getUserInfo(KAKAO_USER_INFO_URL, providerAccessToken);
        return new OAuthIdentity(
                OAuthProvider.KAKAO,
                requiredText(response, "/id"),
                optionalText(response, "/properties/nickname", "/kakao_account/profile/nickname"));
    }

    private OAuthIdentity googleIdentity(String providerAccessToken) {
        JsonNode response = getUserInfo(GOOGLE_USER_INFO_URL, providerAccessToken);
        return new OAuthIdentity(
                OAuthProvider.GOOGLE, requiredText(response, "/sub"), optionalText(response, "/name", "/given_name"));
    }

    private JsonNode getUserInfo(String userInfoUrl, String providerAccessToken) {
        try {
            JsonNode response = restClient
                    .get()
                    .uri(userInfoUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerAccessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
            }
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
    }

    private String requiredText(JsonNode response, String pointer) {
        JsonNode value = response.at(pointer);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
        }
        return value.asString();
    }

    private String optionalText(JsonNode response, String primaryPointer, String fallbackPointer) {
        JsonNode primary = response.at(primaryPointer);
        if (!primary.isMissingNode() && !primary.isNull() && !primary.asString().isBlank()) {
            return primary.asString();
        }

        JsonNode fallback = response.at(fallbackPointer);
        if (!fallback.isMissingNode()
                && !fallback.isNull()
                && !fallback.asString().isBlank()) {
            return fallback.asString();
        }
        return null;
    }
}
