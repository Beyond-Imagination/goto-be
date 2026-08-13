package kr.bi.go_to.service.oauth;

import kr.bi.go_to.enums.OAuthProvider;

public record OAuthIdentity(OAuthProvider provider, String providerId, String suggestedNickname) {}
