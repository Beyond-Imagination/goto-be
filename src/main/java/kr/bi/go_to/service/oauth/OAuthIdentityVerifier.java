package kr.bi.go_to.service.oauth;

import kr.bi.go_to.enums.OAuthProvider;

public interface OAuthIdentityVerifier {

    OAuthIdentity verify(OAuthProvider provider, String providerAccessToken);
}
