-- 1. 약관 마스터 테이블
CREATE TABLE terms (
    id              BIGSERIAL PRIMARY KEY,
    term_key        VARCHAR(50)  NOT NULL UNIQUE,
    bitmask         INT          NOT NULL,
    title           VARCHAR(100) NOT NULL,
    is_required     BOOLEAN      NOT NULL DEFAULT true,
    current_version VARCHAR(20)  NOT NULL DEFAULT '1.0.0',
    effective_date  DATE         NOT NULL,
    summary         TEXT,
    sections        JSONB        NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
    updated_by      VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM'
);

COMMENT ON TABLE terms IS '약관 마스터 정보 테이블';
COMMENT ON COLUMN terms.term_key IS '약관 고유 키 (예: age, terms, privacy, location, marketing)';
COMMENT ON COLUMN terms.bitmask IS '약관 식별 비트마스크 (1, 2, 4, 8, 16)';
COMMENT ON COLUMN terms.title IS '약관 제목';
COMMENT ON COLUMN terms.is_required IS '필수 동의 여부';
COMMENT ON COLUMN terms.current_version IS '현재 최신 버전';
COMMENT ON COLUMN terms.effective_date IS '시행일자';
COMMENT ON COLUMN terms.summary IS '약관 요약문';
COMMENT ON COLUMN terms.sections IS '조항 목록 (JSONB)';
COMMENT ON COLUMN terms.is_active IS '활성화 여부';

-- 2. 약관 버전 이력 테이블 (약관 개정 시 감사 추적)
CREATE TABLE term_histories (
    id             BIGSERIAL PRIMARY KEY,
    term_id        BIGINT       NOT NULL REFERENCES terms(id) ON DELETE CASCADE,
    version        VARCHAR(20)  NOT NULL,
    effective_date DATE         NOT NULL,
    summary        TEXT,
    sections       JSONB        NOT NULL,
    change_log     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
    CONSTRAINT uq_term_history_version UNIQUE (term_id, version)
);

COMMENT ON TABLE term_histories IS '약관 개정 이력 테이블';
COMMENT ON COLUMN term_histories.term_id IS '약관 ID (FK)';
COMMENT ON COLUMN term_histories.version IS '개정 버전';
COMMENT ON COLUMN term_histories.change_log IS '개정 사유 및 변경 사항';

-- 3. 유저 약관 동의 이력 테이블 (법적 증빙 감사 로그)
CREATE TABLE user_term_agreements (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    term_key       VARCHAR(50)  NOT NULL,
    agreed_version VARCHAR(20)  NOT NULL,
    is_agreed      BOOLEAN      NOT NULL,
    agreed_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_ip      VARCHAR(45),
    user_agent     VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_term_agreement_version UNIQUE (user_id, term_key, agreed_version)
);

CREATE INDEX idx_user_term_agreements_user ON user_term_agreements(user_id);

COMMENT ON TABLE user_term_agreements IS '사용자 약관 동의 이력 감사 테이블';
COMMENT ON COLUMN user_term_agreements.user_id IS '회원 ID';
COMMENT ON COLUMN user_term_agreements.term_key IS '동의한 약관 키';
COMMENT ON COLUMN user_term_agreements.agreed_version IS '동의 시점 약관 버전';
COMMENT ON COLUMN user_term_agreements.is_agreed IS '동의 여부 (true/false)';
COMMENT ON COLUMN user_term_agreements.agreed_at IS '동의 일시';
COMMENT ON COLUMN user_term_agreements.client_ip IS '동의 시점 클라이언트 IP';
COMMENT ON COLUMN user_term_agreements.user_agent IS '동의 시점 User-Agent 헤더';

-- 초기 시드 데이터 등록 (5종 약관)
INSERT INTO terms (term_key, bitmask, title, is_required, current_version, effective_date, summary, sections, is_active, created_by, updated_by)
VALUES
(
    'age',
    1,
    '만 14세 이상 확인',
    true,
    '1.0.0',
    '2026-08-01',
    '정보통신망 이용촉진 및 정보보호 등에 관한 법률에 따라 만 14세 미만 아동의 회원가입을 제한합니다.',
    '[{"title": "제 1 조 (연령 확인)", "content": "본 서비스는 만 14세 이상의 사용자를 대상으로 제공됩니다. 만 14세 미만 아동의 경우 법정대리인의 동의 없이 서비스를 이용할 수 없습니다."}, {"title": "제 2 조 (허위 정보에 대한 책임)", "content": "연령 정보를 허위로 기재하여 발생한 법적 책임은 이용자 본인에게 있으며, 운영팀은 확인 즉시 이용 권한을 제한하거나 계정을 삭제할 수 있습니다."}]'::jsonb,
    true,
    'SYSTEM',
    'SYSTEM'
),
(
    'terms',
    2,
    '서비스 이용약관',
    true,
    '1.0.0',
    '2026-08-01',
    'Beyond-Imagination 개발 동아리가 제공하는 "함께가길" 서비스의 이용조건 및 책임사항을 안내합니다.',
    '[{"title": "제 1 조 (목적)", "content": "본 약관은 비영리 개발 단체 \"Beyond-Imagination 개발 동아리\"(이하 \"운영팀\")가 제공하는 위치 기반 보행 접근성 및 이동 지원 서비스 \"함께가길\"(이하 \"서비스\")의 이용조건, 절차 및 운영팀과 회원 간의 권리, 의무, 책임사항을 규정함을 목적으로 합니다."}, {"title": "제 2 조 (용어의 정의)", "content": "본 약관에서 사용하는 주요 용어의 정의는 다음과 같습니다.", "items": ["1. \"서비스\"란 이동 약자 및 보행자를 위한 이동 경로 안내, 장애물 제보, \"도움요청\" 등 공익적 이동 편의를 제공하는 모바일 플랫폼을 의미합니다.", "2. \"회원\"이란 본 약관에 동의하고 소셜 계정 연동을 통해 가입을 완료한 자를 말합니다.", "3. \"제보\"란 회원이 서비스 내에 등록한 장애물(턱, 계단, 공사구간, 파손 등) 및 접근성 관련 사진, 글 등의 게시물을 말합니다.", "4. \"도움요청\"이란 보행 중 지원이 필요한 이용자가 주변 이용자 또는 커뮤니티에 도움을 요청하고 매칭받을 수 있는 기능을 말합니다."]}, {"title": "제 3 조 (비영리 무료 서비스의 특성 및 면책)", "content": "본 서비스는 비영리 목적으로 무료로 제공되며, 다음 각 호의 면책 사항이 적용됩니다.", "items": ["1. 서비스에서 제공하는 지도, 보행 경로, 장애물 정보는 이용자의 자발적 제보 및 공공 데이터를 기반으로 하므로, 실제 현장 환경(기상, 공사, 도로 통행 변화 등)과 일시적으로 다를 수 있으며 보조적 참고 정보로만 제공됩니다.", "2. 운영팀은 회원이 서비스를 이용하여 기대하는 특정 보행 경로의 완전성이나 무결성을 보증하지 않으며, 고의 또는 중대한 과실이 없는 한 무료 서비스 이용으로 인해 발생한 손해에 대해 책임을 면합니다.", "3. \"도움요청\" 기능 이용 시 발생할 수 있는 회원 간의 자발적 상호작용 및 오프라인 도움 과정에서 발생하는 분쟁이나 사고에 대해 운영팀은 고의 또는 중과실이 없는 한 책임을 지지 않습니다."]}, {"title": "제 4 조 (제보 게시물의 저작권 및 공익적 활용)", "content": "회원이 등록한 제보(사진, 설명, 위치 등)의 저작권은 회원에게 귀속됩니다. 다만 회원은 운영팀에게 서비스 운영, 보행 지도 개선, 공익적 연구 및 통계 분석 목적으로 해당 제보를 비독점적, 무상으로 이용할 수 있는 권리를 허락합니다."}, {"title": "제 5 조 (회원의 의무 및 이용 제한)", "content": "회원은 서비스를 이용함에 있어 다음 각 호의 행위를 하여서는 안 됩니다.", "items": ["1. 타인의 명의 또는 정보를 도용하여 가입하는 행위", "2. 허위 또는 조작된 장애물 제보나 장난성 \"도움요청\"을 등록하는 행위", "3. 시스템의 안정적인 운영을 방해하거나 비정상적인 접근을 시도하는 행위"]}, {"title": "제 6 조 (공식 문의처)", "content": "서비스 이용에 관한 문의, 건의 및 불편 사항은 공식 문의 이메일(team@beyond-imagination.net)을 통해 접수하실 수 있습니다."}]'::jsonb,
    true,
    'SYSTEM',
    'SYSTEM'
),
(
    'privacy',
    4,
    '개인정보 수집 및 이용 동의',
    true,
    '1.0.0',
    '2026-08-01',
    'Beyond-Imagination은 최소한의 개인정보만을 수집하며 안전하게 암호화하여 관리합니다.',
    '[{"title": "제 1 조 (수집하는 개인정보 항목)", "content": "운영팀은 원활한 서비스 제공을 위해 다음의 개인정보를 수집합니다.", "items": ["1. 소셜 로그인 시: 소셜 고유 식별자(ID), 프로필 닉네임, 프로필 이미지 URL", "2. 서비스 이용 시: 닉네임, 장애물 제보 데이터, \"도움요청\" 이용 기록, 이동 선호도 설정", "3. 접속 시 자동 생성 정보: 접속 일시, 서비스 이용 기록, 기기 식별 정보"]}, {"title": "제 2 조 (개인정보의 수집 및 이용 목적)", "content": "수집된 개인정보는 다음 목적을 위해서만 이용됩니다.", "items": ["1. 회원 식별 및 서비스 가입 의사 확인", "2. 맞춤형 보행 경로 및 접근성 정보 제공", "3. 장애물 제보 관리 및 \"도움요청\" 기능 연동", "4. 서비스 품질 개선, 통계 분석 및 보안 강화"]}, {"title": "제 3 조 (개인정보의 보유, 이용 기간 및 파기)", "content": "회원의 개인정보는 회원 탈퇴 시 지체 없이 파기됩니다. 단, 서비스 오남용 방지 및 \"도움요청\" 이용 이력 로그는 서비스 개선 및 분쟁 예방을 위해 암호화되어 내부 보안 정책에 따라 최소한의 기간 동안 보관 후 안전하게 영구 파기됩니다."}, {"title": "제 4 조 (개인정보 보호담당자)", "content": "운영팀은 회원의 개인정보를 보호하고 관련 불만을 처리하기 위해 개인정보 보호담당자를 지정하고 있습니다.\n- 담당: Beyond-Imagination 개인정보 보호담당\n- 이메일: team@beyond-imagination.net"}]'::jsonb,
    true,
    'SYSTEM',
    'SYSTEM'
),
(
    'location',
    8,
    '위치기반서비스 이용약관',
    true,
    '1.0.0',
    '2026-08-01',
    '주변 보행 환경 안내 및 "도움요청" 기능을 위한 위치정보 이용 및 보안 관리 방침을 안내합니다.',
    '[{"title": "제 1 조 (목적 및 위치정보의 이용)", "content": "운영팀은 회원의 현재 위치를 기반으로 주변의 이동 경로, 턱/계단/경사로 등 보행 장애물 정보를 지도 상에 표시하고, 실시간 \"도움요청\" 서비스 매칭을 제공하기 위해 위치정보를 활용합니다."}, {"title": "제 2 조 (위치정보의 저장, 보안 및 암호화 관리)", "content": "운영팀은 회원의 위치정보를 다음과 같이 안전하게 보호합니다.", "items": ["1. 일반적인 보행 정보 조회의 경우 실시간 확인 목적으로만 일시적으로 사용하며 불필요하게 서버에 영구 보관하지 않습니다.", "2. \"도움요청\" 이용 로그 및 서비스 개선을 위해 수집되는 위치 이력 데이터는 강력한 암호화 기법을 적용하여 안전하게 관리하며, 제3자에게 임의로 제공하거나 공유하지 않습니다."]}, {"title": "제 3 조 (위치정보 이용에 대한 권리 및 철회)", "content": "회원은 기기 설정 또는 앱 설정을 통해 언제든지 위치정보 수집 동의를 철회할 수 있습니다. 동의를 철회할 경우 현재 위치 기반의 실시간 안내 및 \"도움요청\" 기능 이용이 제한될 수 있습니다."}, {"title": "제 4 조 (위치정보 관리담당자)", "content": "위치기반서비스와 관련된 문의 및 권리 행사는 아래의 위치정보 관리담당자에게 연락하실 수 있습니다.\n- 담당: Beyond-Imagination 위치정보 관리담당\n- 이메일: team@beyond-imagination.net"}]'::jsonb,
    true,
    'SYSTEM',
    'SYSTEM'
),
(
    'marketing',
    16,
    '마케팅 및 공지 정보 수신 동의',
    false,
    '1.0.0',
    '2026-08-01',
    '함께가길의 신규 기능 업데이트, 공익 이벤트 및 공지사항을 받아보실 수 있습니다.',
    '[{"title": "제 1 조 (수신 목적)", "content": "Beyond-Imagination 개발 동아리에서 제공하는 함께가길 서비스의 새로운 기능 업데이트, 공익 캠페인/이벤트 안내 및 중요 공지사항 제공을 목적으로 알림(앱 푸시 등)을 발송합니다."}, {"title": "제 2 조 (선택 동의 및 철회)", "content": "마케팅 및 공지 정보 수신 동의는 선택 사항이며, 동의하지 않더라도 필수 보행 서비스 이용에는 아무런 제한이 없습니다. 또한 앱 내 설정 메뉴에서 언제든지 수신 여부를 변경하실 수 있습니다."}]'::jsonb,
    true,
    'SYSTEM',
    'SYSTEM'
);

-- 초기 이력 데이터 등록
INSERT INTO term_histories (term_id, version, effective_date, summary, sections, change_log, created_by)
SELECT id, current_version, effective_date, summary, sections, '최초 제정', 'SYSTEM'
FROM terms;
