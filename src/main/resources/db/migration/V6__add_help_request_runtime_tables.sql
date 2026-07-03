CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE members
    ADD CONSTRAINT uk_members_nickname UNIQUE (nickname);

ALTER TABLE refresh_tokens
    RENAME COLUMN username TO subject;

ALTER TABLE help_matching_logs
    ALTER COLUMN place_id DROP NOT NULL;
ALTER TABLE help_matching_logs
    ADD COLUMN help_request_id UUID UNIQUE;

CREATE TABLE help_requests
(
    id             UUID PRIMARY KEY,
    place_id       BIGINT,
    requester_id   BIGINT         NOT NULL,
    helper_id      BIGINT,
    location_label VARCHAR(255)   NOT NULL,
    latitude       NUMERIC(10, 7) NOT NULL,
    longitude      NUMERIC(10, 7) NOT NULL,
    floor_level    INT,
    message        VARCHAR(500),
    status         VARCHAR(20)    NOT NULL,
    requested_at   TIMESTAMPTZ    NOT NULL,
    expires_at     TIMESTAMPTZ    NOT NULL,
    accepted_at    TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    canceled_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    -- 카스케이드를 거는게 문제의 소지가 있지만 이미 컨벤션이 그렇게 잡혀서 걸어둔다
    CONSTRAINT fk_help_requests_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT fk_help_requests_requester FOREIGN KEY (requester_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_help_requests_helper FOREIGN KEY (helper_id) REFERENCES members (id) ON DELETE SET NULL
);

CREATE TABLE help_request_rejections
(
    id              BIGSERIAL PRIMARY KEY,
    help_request_id UUID        NOT NULL REFERENCES help_requests (id) ON DELETE CASCADE,
    member_id       BIGINT      NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    rejected_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_help_request_rejections_request_member UNIQUE (help_request_id, member_id)
);

CREATE INDEX idx_help_requests_status_expires_at ON help_requests (status, expires_at);
CREATE INDEX idx_help_requests_requester_id_requested_at ON help_requests (requester_id, requested_at DESC);
CREATE INDEX idx_help_requests_helper_id_requested_at ON help_requests (helper_id, requested_at DESC);
CREATE INDEX idx_help_requests_latitude_longitude ON help_requests (latitude, longitude);
CREATE INDEX idx_help_requests_location_geography ON help_requests
    USING GIST ((ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography));

ALTER TABLE help_matching_logs
    ADD CONSTRAINT fk_help_matching_logs_help_request
        FOREIGN KEY (help_request_id) REFERENCES help_requests (id) ON DELETE SET NULL;

COMMENT ON COLUMN members.nickname IS '사용자 표시명이며 현재 임시 로그인에서 사용하는 닉네임';

COMMENT ON COLUMN refresh_tokens.subject IS 'JWT subject와 연결되는 사용자 ID 문자열';

COMMENT ON TABLE help_requests IS '실시간 도움 요청 상태를 관리하는 엔티티';
COMMENT ON COLUMN help_requests.id IS '실시간 도움 요청 고유 식별자';
COMMENT ON COLUMN help_requests.place_id IS '장소 기반 요청일 때 연결되는 장소 ID. 길 위 현재 위치 요청이면 NULL';
COMMENT ON COLUMN help_requests.requester_id IS '도움을 요청한 사용자 ID';
COMMENT ON COLUMN help_requests.helper_id IS '요청을 수락하여 도움을 제공하는 사용자 ID';
COMMENT ON COLUMN help_requests.location_label IS '사용자와 도우미가 이해할 수 있는 현재 위치 설명';
COMMENT ON COLUMN help_requests.latitude IS '도움 요청 발생 위치의 위도';
COMMENT ON COLUMN help_requests.longitude IS '도움 요청 발생 위치의 경도';
COMMENT ON COLUMN help_requests.floor_level IS '실내 또는 복합공간 요청에서의 층수. 실외 요청이면 NULL 가능';
COMMENT ON COLUMN help_requests.message IS '요청자가 입력한 도움 요청 상세 메시지';
COMMENT ON COLUMN help_requests.status IS '도움 요청 상태. REQUESTED, ACCEPTED, COMPLETED, CANCELED, EXPIRED';
COMMENT ON COLUMN help_requests.requested_at IS '도움 요청을 생성한 시각';
COMMENT ON COLUMN help_requests.expires_at IS '요청이 자동 만료되는 시각';
COMMENT ON COLUMN help_requests.accepted_at IS '도우미가 요청을 수락한 시각';
COMMENT ON COLUMN help_requests.completed_at IS '도움이 완료된 시각';
COMMENT ON COLUMN help_requests.canceled_at IS '요청자가 도움 요청을 취소한 시각';
COMMENT ON COLUMN help_requests.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN help_requests.updated_at IS '엔티티 최종 수정 일시';

COMMENT ON TABLE help_request_rejections IS '사용자별 도움 요청 거절 기록';
COMMENT ON COLUMN help_request_rejections.id IS '도움 요청 거절 기록 고유 식별자';
COMMENT ON COLUMN help_request_rejections.help_request_id IS '거절한 도움 요청 ID';
COMMENT ON COLUMN help_request_rejections.member_id IS '도움 요청을 거절한 사용자 ID';
COMMENT ON COLUMN help_request_rejections.rejected_at IS '사용자가 도움 요청을 거절한 시각';
