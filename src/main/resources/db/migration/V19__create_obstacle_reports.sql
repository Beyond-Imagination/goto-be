CREATE TABLE obstacle_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    location_point geometry(Point, 4326) NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    confirmed_count INTEGER NOT NULL DEFAULT 0,
    last_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_obstacle_reports_reporter FOREIGN KEY (reporter_id) REFERENCES members (id) ON DELETE CASCADE
);

CREATE INDEX idx_obstacle_reports_location_point ON obstacle_reports USING GIST (location_point);

COMMENT ON TABLE obstacle_reports IS '실외 보행 경로상의 통행 장애 지점에 대한 사용자 제보. 같은 지점에 대한 여러 제보는 병합되지 않는 독립 이벤트다';
COMMENT ON COLUMN obstacle_reports.id IS '장애물 제보 고유 식별자 (PK)';
COMMENT ON COLUMN obstacle_reports.reporter_id IS '제보를 작성한 회원 엔티티 (N:1 관계)';
COMMENT ON COLUMN obstacle_reports.location_point IS '장애물의 절대 위치. PLACE와는 연관관계를 갖지 않는다';
COMMENT ON COLUMN obstacle_reports.issue_type IS '장애물 유형 (STAIRS, HIGH_CURB, STEEP_SLOPE, NARROW_PASSAGE, CONSTRUCTION, SIDEWALK_DAMAGE, LONG_WALKING_DISTANCE)';
COMMENT ON COLUMN obstacle_reports.severity IS '심각도 (IMPASSABLE, CAUTION, INFO)';
COMMENT ON COLUMN obstacle_reports.status IS '생명주기 상태 (ACTIVE, RESOLVED). RESOLVED는 재오픈되지 않는다';
COMMENT ON COLUMN obstacle_reports.confirmed_count IS '"아직 있어요" 확인을 남긴 서로 다른 회원 수 (obstacle_report_confirmations row 수와 동기화된 파생 카운터)';
COMMENT ON COLUMN obstacle_reports.last_confirmed_at IS '마지막으로 확인이 들어온 시각. STALE 라벨은 이 값(없으면 created_at) 기준으로 조회 시점에 계산되며 DB에 저장되지 않는다';

CREATE TABLE obstacle_report_affected_mobility_types (
    obstacle_report_id BIGINT NOT NULL,
    mobility_type VARCHAR(50) NOT NULL,
    CONSTRAINT fk_obstacle_report_mobility_types_report FOREIGN KEY (obstacle_report_id) REFERENCES obstacle_reports (id) ON DELETE CASCADE,
    CONSTRAINT uk_obstacle_report_mobility_types UNIQUE (obstacle_report_id, mobility_type)
);

COMMENT ON TABLE obstacle_report_affected_mobility_types IS '제보가 영향을 주는 이동조건 유형 (휠체어/유모차/느린보행) 다중 선택. 홈 지도 이동조건 필터가 클러스터를 필터링하는 데 쓰인다';

CREATE TABLE obstacle_report_photo_urls (
    obstacle_report_id BIGINT NOT NULL,
    photo_order INTEGER NOT NULL,
    photo_url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (obstacle_report_id, photo_order),
    CONSTRAINT fk_obstacle_report_photo_urls_report FOREIGN KEY (obstacle_report_id) REFERENCES obstacle_reports (id) ON DELETE CASCADE
);

COMMENT ON TABLE obstacle_report_photo_urls IS '이미 호스팅된 사진 URL 목록. 업로드 인프라는 이번 스코프에 포함하지 않으며, 이미 존재하는 URL 문자열만 저장한다';

CREATE TABLE obstacle_report_confirmations (
    id BIGSERIAL PRIMARY KEY,
    obstacle_report_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_obstacle_report_confirmations_report FOREIGN KEY (obstacle_report_id) REFERENCES obstacle_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_obstacle_report_confirmations_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT uk_obstacle_report_confirmations_report_id_member_id UNIQUE (obstacle_report_id, member_id)
);

COMMENT ON TABLE obstacle_report_confirmations IS '회원이 특정 ObstacleReport를 보고 "아직 있어요"를 눌러 남기는 확인 기록. 존재 자체가 확인 기록이며, 회원당 1회만 카운트된다';
COMMENT ON COLUMN obstacle_report_confirmations.id IS '확인 기록 고유 식별자 (PK)';
COMMENT ON COLUMN obstacle_report_confirmations.obstacle_report_id IS '확인 대상 장애물 제보 엔티티 (N:1 관계)';
COMMENT ON COLUMN obstacle_report_confirmations.member_id IS '확인한 회원 엔티티 (N:1 관계)';
