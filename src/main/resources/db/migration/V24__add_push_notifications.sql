-- FCM 푸시 알림 도입 (기기 토큰 · 알림 이력 · 발송 보조 컬럼)

-- 1. 기기 토큰
-- 푸시는 "회원"이 아니라 "기기"로 간다. 한 회원이 여러 기기를 쓸 수 있고,
-- 같은 기기를 다른 계정으로 로그인하면 토큰의 주인만 바뀐다(토큰이 유일 키인 이유).
CREATE TABLE device_tokens
(
    id                 BIGSERIAL PRIMARY KEY,
    member_id          BIGINT       NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    token              VARCHAR(512) NOT NULL,
    platform           VARCHAR(20)  NOT NULL,
    app_version        VARCHAR(50),
    -- 「주변 도움 요청」 푸시를 보낼 반경 계산에 쓰는 마지막 위치.
    -- 위치 권한이 없거나 아직 모르면 NULL이고, 그때는 반경 대상에서 빠진다.
    -- 저장 시 소수점 3자리(약 110m)로 줄인다.
    last_latitude      DOUBLE PRECISION,
    last_longitude     DOUBLE PRECISION,
    last_location_at   TIMESTAMPTZ,
    last_registered_at TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_member ON device_tokens (member_id);

-- 마지막 위치는 반경 질의(ST_DWithin)로만 쓰므로 geography 생성 컬럼 + GiST 인덱스를 둔다.
-- 위경도가 없는 행은 NULL이라 인덱스·질의 양쪽에서 자연히 제외된다.
ALTER TABLE device_tokens
    ADD COLUMN last_location GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (
            CASE
                WHEN last_latitude IS NULL OR last_longitude IS NULL THEN NULL
                ELSE ST_SetSRID(ST_MakePoint(last_longitude, last_latitude), 4326)::geography
                END
            ) STORED;

CREATE INDEX idx_device_tokens_last_location ON device_tokens USING GIST (last_location);

COMMENT ON TABLE device_tokens IS 'FCM 푸시를 보낼 기기 토큰. 토큰 하나당 한 행이며 주인(member_id)은 바뀔 수 있다.';
COMMENT ON COLUMN device_tokens.platform IS 'ANDROID / IOS';
COMMENT ON COLUMN device_tokens.last_location_at IS '마지막 위치를 보고한 시각. 오래된 위치는 반경 발송에서 제외한다.';
COMMENT ON COLUMN device_tokens.last_registered_at IS '앱이 마지막으로 이 토큰을 등록·갱신한 시각.';

-- 2. 알림 이력
-- 앱의 알림 목록(저장 04 · 상태 변경 알림)이 이 테이블을 읽는다.
-- 푸시는 "지나가면 사라지는" 전달 수단이라, 나중에 다시 확인하려면 서버에 남겨야 한다.
CREATE TABLE notifications
(
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT       NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    -- 알림을 눌렀을 때 열 화면과 파라미터. 푸시 payload와 같은 값이다.
    route           VARCHAR(200),
    place_id        BIGINT,
    report_id       BIGINT,
    help_request_id UUID,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- 목록은 항상 "내 알림을 최신순으로"라서 두 컬럼을 함께 건다.
-- id를 포함해야 같은 시각의 행에서도 커서 페이지네이션이 흔들리지 않는다.
CREATE INDEX idx_notifications_member_created ON notifications (member_id, created_at DESC, id DESC);

-- 안 읽은 개수(배지)는 자주 조회되므로 부분 인덱스를 둔다.
CREATE INDEX idx_notifications_member_unread ON notifications (member_id) WHERE read_at IS NULL;

COMMENT ON TABLE notifications IS '회원에게 보낸 알림 이력. 푸시 발송 여부와 무관하게 대상이 되면 남는다.';
COMMENT ON COLUMN notifications.read_at IS '사용자가 읽은 시각. NULL이면 안 읽음.';

-- 3. 「내 제보 확인 요청」 알림을 언제 보냈는지
-- 이 값이 없으면 오래된 제보에 매일 같은 알림을 보내게 된다.
ALTER TABLE obstacle_reports
    ADD COLUMN confirmation_requested_at TIMESTAMPTZ;

COMMENT ON COLUMN obstacle_reports.confirmation_requested_at IS '제보자에게 마지막으로 "지금도 그대로인가요?" 알림을 보낸 시각';

-- 4. 「주변 도움 요청」 푸시의 2단계 확대 발송 기록
-- 1단계는 요청 즉시 "방금 위치를 보고한 기기"에만 보내고, 수락이 없으면 더 넓은 대상으로 한 번 더 보낸다.
-- 이 값이 없으면 확대 발송이 매분 반복된다.
ALTER TABLE help_requests
    ADD COLUMN push_escalated_at TIMESTAMPTZ;

COMMENT ON COLUMN help_requests.push_escalated_at IS '주변 도움 요청 푸시를 더 넓은 대상으로 확대 발송한 시각. NULL이면 아직 확대 전.';
