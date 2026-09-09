-- 제보 확장 (GOTO-120)
--  1. 장애물 제보 유형 추가 (적치물·불법주차·점자블록 훼손·미끄러운 길·기타)
--  2. 장애물 제보 메모(description)
--  3. 장소 단위 상태 제보 테이블
--  4. 시설 상태 제보 이슈 유형 정리

-- 1. issue_type은 VARCHAR(50)이고 CHECK 제약이 없어 스키마 변경은 필요하지 않으며,
--    컬럼 주석만 실제 enum 값과 맞춘다.
COMMENT ON COLUMN obstacle_reports.issue_type IS '장애물 유형 (STAIRS, HIGH_CURB, STEEP_SLOPE, NARROW_PASSAGE, CONSTRUCTION, SIDEWALK_DAMAGE, LONG_WALKING_DISTANCE, OBSTRUCTION, ILLEGAL_PARKING, BRAILLE_BLOCK_DAMAGE, SLIPPERY_SURFACE, OTHER)';

-- 1-1. 업로드 API(POST /api/v1/uploads/images)가 생겨 V19의 "업로드 인프라는 이번 스코프 밖" 주석은 더 이상 맞지 않는다.
COMMENT ON TABLE obstacle_report_photo_urls IS '사진 URL 목록. 업로드 API(POST /api/v1/uploads/images)로 올린 뒤 받은 URL만 저장한다';

-- 2. 장애물 제보 메모
ALTER TABLE obstacle_reports ADD COLUMN description TEXT;

COMMENT ON COLUMN obstacle_reports.description IS '제보자가 남긴 메모 (선택 입력). 유형·심각도만으로 전달되지 않는 상황 설명';

-- 3. 장소 단위 상태 제보
CREATE TABLE place_state_reports (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    access_status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_place_state_reports_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_place_state_reports_reporter FOREIGN KEY (reporter_id) REFERENCES members (id) ON DELETE CASCADE
);

CREATE INDEX idx_place_state_reports_place_id_created_at ON place_state_reports (place_id, created_at DESC);
CREATE INDEX idx_place_state_reports_reporter_id_created_at ON place_state_reports (reporter_id, created_at DESC);

COMMENT ON TABLE place_state_reports IS '장소(공원, 건물, 화장실 등) 단위의 사용자 상태 제보. 장애물 제보와 달리 좌표가 아니라 장소에 붙으며, 외부 동기화 데이터인 place_bf_info를 덮어쓰지 않고 시간순 이벤트로 쌓인다';
COMMENT ON COLUMN place_state_reports.id IS '장소 상태 제보 고유 식별자 (PK)';
COMMENT ON COLUMN place_state_reports.place_id IS '제보 대상 장소 엔티티 (N:1 관계). 표시할 위치·주소는 항상 이 장소에서 온다';
COMMENT ON COLUMN place_state_reports.reporter_id IS '제보를 작성한 회원 엔티티 (N:1 관계)';
COMMENT ON COLUMN place_state_reports.access_status IS '장소 전반의 이용 난이도 (ACCESSIBLE, PARTIALLY_ACCESSIBLE, INACCESSIBLE)';
COMMENT ON COLUMN place_state_reports.description IS '제보자가 남긴 메모 (선택 입력)';

CREATE TABLE place_state_report_facility_statuses (
    place_state_report_id BIGINT NOT NULL,
    facility VARCHAR(50) NOT NULL,
    facility_status VARCHAR(50) NOT NULL,
    PRIMARY KEY (place_state_report_id, facility),
    CONSTRAINT fk_place_state_report_facility_statuses_report FOREIGN KEY (place_state_report_id) REFERENCES place_state_reports (id) ON DELETE CASCADE
);

COMMENT ON TABLE place_state_report_facility_statuses IS '제보에 포함된 편의시설별 상태. 확인하지 못한 항목은 행 자체가 없으며, "없음"(UNAVAILABLE)과 "확인 못 함"은 구분된다';
COMMENT ON COLUMN place_state_report_facility_statuses.facility IS '편의시설 종류 (ELEVATOR, ACCESSIBLE_TOILET, RAMP, PARKING)';
COMMENT ON COLUMN place_state_report_facility_statuses.facility_status IS '해당 편의시설 상태 (AVAILABLE, UNAVAILABLE, BROKEN)';

CREATE TABLE place_state_report_photo_urls (
    place_state_report_id BIGINT NOT NULL,
    photo_order INTEGER NOT NULL,
    photo_url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (place_state_report_id, photo_order),
    CONSTRAINT fk_place_state_report_photo_urls_report FOREIGN KEY (place_state_report_id) REFERENCES place_state_reports (id) ON DELETE CASCADE
);

COMMENT ON TABLE place_state_report_photo_urls IS '장소 상태 제보에 첨부된 사진 URL 목록. 업로드 API(POST /api/v1/uploads/images)로 올린 뒤 받은 URL만 저장한다';

-- 4. reports.issue_type도 VARCHAR(50) 문자열 그대로 두고, FacilityIssueType enum 값으로 주석만 맞춘다.
--    예전 데이터에는 이 목록에 없는 값이 남아 있을 수 있어 CHECK 제약은 걸지 않는다.
COMMENT ON COLUMN reports.issue_type IS '시설 이슈 유형 (BROKEN, OUT_OF_SERVICE, BLOCKED, DAMAGED, MISSING, REPAIRED, OTHER)';
