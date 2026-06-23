CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE members (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE members IS '서비스 사용자(멤버) 정보를 관리하는 엔티티';
COMMENT ON COLUMN members.id IS '멤버 고유 식별자 (PK)';
COMMENT ON COLUMN members.role IS '멤버의 권한 역할 (예: USER, ADMIN)';
COMMENT ON COLUMN members.nickname IS '멤버의 닉네임';
COMMENT ON COLUMN members.created_at IS '계정 생성 일시 (엔티티 최초 생성 일시)';
COMMENT ON COLUMN members.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE places (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    source VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    name VARCHAR(255) NOT NULL,
    sanitized_address VARCHAR(500),
    location_point geometry(Point, 4326),
    thumbnail_url VARCHAR(1000),
    overview TEXT,
    homepage VARCHAR(1000),
    tel VARCHAR(100),
    content_type_id VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_places_external_id_source UNIQUE (external_id, source)
);

CREATE INDEX idx_places_location_point ON places USING GIST (location_point);
CREATE INDEX idx_places_name_trgm ON places USING GIN (name gin_trgm_ops);

COMMENT ON TABLE places IS '관광지 및 장소의 기본 정보를 관리하는 엔티티';
COMMENT ON COLUMN places.id IS '장소 고유 식별자 (내부 PK)';
COMMENT ON COLUMN places.external_id IS '외부 시스템(한국관광공사 등)에서의 원본 장소 ID (예: contentId)';
COMMENT ON COLUMN places.source IS '데이터 출처 (예: KNTO, SSIS, USER 등)';
COMMENT ON COLUMN places.category IS '장소 카테고리 (관광지, 숙박, 공공기관 등)';
COMMENT ON COLUMN places.name IS '장소명';
COMMENT ON COLUMN places.sanitized_address IS '도로명/지번 등 정제된 형태의 주소';
COMMENT ON COLUMN places.location_point IS '장소의 위도/경도 기반 공간 데이터(Point) 정보';
COMMENT ON COLUMN places.thumbnail_url IS '장소의 대표 썸네일 이미지 URL';
COMMENT ON COLUMN places.overview IS '장소에 대한 상세 설명 및 개요 텍스트';
COMMENT ON COLUMN places.homepage IS '장소의 공식 홈페이지 URL';
COMMENT ON COLUMN places.tel IS '장소의 대표 연락처/전화번호';
COMMENT ON COLUMN places.content_type_id IS '한국관광공사 기준 관광타입 ID (예: 12-관광지, 32-숙박 등)';
COMMENT ON COLUMN places.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN places.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE place_bf_info (
    place_id BIGINT PRIMARY KEY,
    bf_details JSONB,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_place_bf_info_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
);

COMMENT ON TABLE place_bf_info IS '장소의 무장애(Barrier-Free) 편의시설 상세 정보를 관리하는 엔티티';
COMMENT ON COLUMN place_bf_info.place_id IS '무장애 정보를 소유한 장소 ID (PK 겸 FK)';
COMMENT ON COLUMN place_bf_info.bf_details IS '수유실, 점자블록, 휠체어 대여 여부 등 100여 개의 편의시설 메타데이터를 구조화한 JSON';
COMMENT ON COLUMN place_bf_info.last_synced_at IS '해당 무장애 정보가 외부 API로부터 동기화된 최근 일시';
COMMENT ON COLUMN place_bf_info.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN place_bf_info.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE floor_maps (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL,
    floor_level INT NOT NULL,
    geojson_data JSONB,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_floor_maps_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_floor_maps_created_by FOREIGN KEY (created_by) REFERENCES members (id) ON DELETE SET NULL
);

COMMENT ON TABLE floor_maps IS '특정 장소의 실내 층별 지도(도면)를 관리하는 엔티티';
COMMENT ON COLUMN floor_maps.id IS '도면 고유 식별자 (PK)';
COMMENT ON COLUMN floor_maps.place_id IS '도면이 속한 장소 엔티티 (N:1 관계)';
COMMENT ON COLUMN floor_maps.floor_level IS '도면의 층수 (예: 1, 2, -1(지하), 0(실외) 등)';
COMMENT ON COLUMN floor_maps.geojson_data IS '실내 공간을 렌더링하기 위한 Mapbox 용도의 벡터 폴리곤 GeoJSON 데이터';
COMMENT ON COLUMN floor_maps.created_by IS '도면 데이터를 최초로 생성하거나 업로드한 작성자';
COMMENT ON COLUMN floor_maps.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN floor_maps.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE facility_nodes (
    id BIGSERIAL PRIMARY KEY,
    floor_map_id BIGINT NOT NULL,
    target_feature_id VARCHAR(100),
    node_type VARCHAR(50) NOT NULL,
    name VARCHAR(255),
    geojson_point geometry(Point, 4326),
    is_checkpoint BOOLEAN NOT NULL DEFAULT FALSE,
    snap_radius INT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_facility_nodes_floor_map FOREIGN KEY (floor_map_id) REFERENCES floor_maps (id) ON DELETE CASCADE,
    CONSTRAINT fk_facility_nodes_created_by FOREIGN KEY (created_by) REFERENCES members (id) ON DELETE SET NULL
);

COMMENT ON TABLE facility_nodes IS '실내 지도상에 위치한 엘리베이터, 화장실 등의 개별 편의시설 노드를 관리하는 엔티티';
COMMENT ON COLUMN facility_nodes.id IS '시설물 노드 고유 식별자 (PK)';
COMMENT ON COLUMN facility_nodes.floor_map_id IS '시설물이 속한 실내 층별 지도 엔티티 (N:1 관계)';
COMMENT ON COLUMN facility_nodes.target_feature_id IS 'GeoJSON 데이터 내 특정 피처와 논리적으로 매핑하기 위한 문자열 고유 식별자';
COMMENT ON COLUMN facility_nodes.node_type IS '시설물의 종류 (예: ELEVATOR, TOILET 등)';
COMMENT ON COLUMN facility_nodes.name IS '시설물 이름 또는 별칭';
COMMENT ON COLUMN facility_nodes.geojson_point IS '실내에서의 절대적인 Point 좌표값';
COMMENT ON COLUMN facility_nodes.is_checkpoint IS 'PDR(보행자 데드레코닝) 센서 이동 궤적을 캘리브레이션할 수 있는 보정 영점 여부';
COMMENT ON COLUMN facility_nodes.snap_radius IS '사용자가 해당 시설물 근처로 접근했을 때 보정을 허용할 오차 반경 (단위: m)';
COMMENT ON COLUMN facility_nodes.created_by IS '시설물 노드 정보를 등록한 사용자';
COMMENT ON COLUMN facility_nodes.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN facility_nodes.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reports_node FOREIGN KEY (node_id) REFERENCES facility_nodes (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES members (id) ON DELETE CASCADE
);

COMMENT ON TABLE reports IS '실시간 편의시설 상태(고장, 수리완료 등)에 대한 사용자 제보 정보를 관리하는 엔티티';
COMMENT ON COLUMN reports.id IS '제보 내역 고유 식별자 (PK)';
COMMENT ON COLUMN reports.node_id IS '제보 대상이 되는 편의시설 노드 엔티티 (N:1 관계)';
COMMENT ON COLUMN reports.reporter_id IS '제보를 작성한 사용자(멤버) 엔티티 (N:1 관계)';
COMMENT ON COLUMN reports.issue_type IS '제보 분류 유형 (예: BROKEN, REPAIRED 등)';
COMMENT ON COLUMN reports.description IS '제보 내용 및 상세 설명';
COMMENT ON COLUMN reports.created_at IS '제보 등록 일시 (엔티티 최초 생성 일시)';
COMMENT ON COLUMN reports.updated_at IS '엔티티 최종 수정 일시';

CREATE TABLE help_matching_logs (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    helper_id BIGINT,
    last_known_location JSONB,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_help_logs_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_help_logs_requester FOREIGN KEY (requester_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_help_logs_helper FOREIGN KEY (helper_id) REFERENCES members (id) ON DELETE SET NULL
);

COMMENT ON TABLE help_matching_logs IS '현장에서 사용자가 도움을 요청하고 도우미가 매칭된 내역을 보관하는 로깅 엔티티';
COMMENT ON COLUMN help_matching_logs.id IS '도움 매칭 내역 고유 식별자 (PK)';
COMMENT ON COLUMN help_matching_logs.place_id IS '도움이 발생한 장소 엔티티 (N:1 관계)';
COMMENT ON COLUMN help_matching_logs.requester_id IS '도움을 요청한 사용자 엔티티 (N:1 관계)';
COMMENT ON COLUMN help_matching_logs.helper_id IS '요청을 수락하여 도움을 제공한 사용자 엔티티 (N:1 관계)';
COMMENT ON COLUMN help_matching_logs.last_known_location IS '도움 요청 당시 파악된 요청자의 마지막 위치 (예: 층수, 위경도 등이 담긴 스냅샷 JSON)';
COMMENT ON COLUMN help_matching_logs.requested_at IS '도움 요청 발생 일시';
COMMENT ON COLUMN help_matching_logs.completed_at IS '도움이 완료되거나 매칭이 성사되어 상황이 종료된 일시';
COMMENT ON COLUMN help_matching_logs.created_at IS '엔티티 최초 생성 일시';
COMMENT ON COLUMN help_matching_logs.updated_at IS '엔티티 최종 수정 일시';
