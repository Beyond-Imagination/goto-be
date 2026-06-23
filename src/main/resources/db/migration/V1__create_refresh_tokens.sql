CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE refresh_tokens IS '사용자 리프레시 토큰 관리 엔티티';
COMMENT ON COLUMN refresh_tokens.id IS '리프레시 토큰 고유 식별자 (UUID PK)';
COMMENT ON COLUMN refresh_tokens.username IS '토큰이 발급된 사용자 이름(아이디)';
COMMENT ON COLUMN refresh_tokens.expires_at IS '토큰 만료 일시';
COMMENT ON COLUMN refresh_tokens.revoked IS '토큰 폐기 여부';
COMMENT ON COLUMN refresh_tokens.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN refresh_tokens.updated_at IS '엔티티 최종 수정 일시';
