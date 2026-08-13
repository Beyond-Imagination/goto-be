ALTER TABLE members
    ADD COLUMN agreement_mask BIGINT NOT NULL DEFAULT 15,
    ADD COLUMN preferences JSONB NOT NULL DEFAULT '{"mobilityModes":[],"informationPreferences":{"priorityFacilities":[],"avoidConditions":[]}}'::jsonb,
    ADD CONSTRAINT chk_members_agreement_mask_non_negative CHECK (agreement_mask >= 0),
    ADD CONSTRAINT chk_members_required_agreements CHECK ((agreement_mask & 15) = 15);

CREATE TABLE oauth_users
(
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_oauth_users_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT chk_oauth_users_provider CHECK (provider IN ('NAVER', 'KAKAO', 'GOOGLE')),
    CONSTRAINT uk_oauth_users_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT uk_oauth_users_member_provider UNIQUE (member_id, provider)
);

COMMENT ON COLUMN members.agreement_mask IS '가입 시 동의한 약관 비트마스크. 필수 약관 비트 0~3은 항상 설정된다';
COMMENT ON COLUMN members.preferences IS '이동방식과 우선 확인 시설, 회피 조건을 보관하는 사용자 개인화 JSON';

COMMENT ON TABLE oauth_users IS 'OAuth provider 계정과 서비스 사용자를 연결하는 테이블';
COMMENT ON COLUMN oauth_users.member_id IS 'OAuth 계정이 연결된 서비스 사용자 ID';
COMMENT ON COLUMN oauth_users.provider IS 'OAuth provider: NAVER, KAKAO, GOOGLE';
COMMENT ON COLUMN oauth_users.provider_id IS 'provider가 발급한 변경되지 않는 외부 사용자 식별자';
