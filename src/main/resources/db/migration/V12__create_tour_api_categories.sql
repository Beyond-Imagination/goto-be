CREATE TABLE tour_api_categories
(
    code                 VARCHAR(9)  PRIMARY KEY,
    parent_code          VARCHAR(9),
    depth                SMALLINT    NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    last_seen_sync_token UUID        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tour_api_categories_parent
        FOREIGN KEY (parent_code) REFERENCES tour_api_categories (code),
    CONSTRAINT ck_tour_api_categories_depth
        CHECK (depth BETWEEN 1 AND 3),
    CONSTRAINT ck_tour_api_categories_code_hierarchy
        CHECK (
            (depth = 1
                AND code ~ '^[A-Z][0-9]{2}$'
                AND parent_code IS NULL)
            OR
            (depth = 2
                AND code ~ '^[A-Z][0-9]{4}$'
                AND parent_code = LEFT(code, 3))
            OR
            (depth = 3
                AND code ~ '^[A-Z][0-9]{8}$'
                AND parent_code = LEFT(code, 5))
        ),
    CONSTRAINT ck_tour_api_categories_name_not_blank
        CHECK (BTRIM(name) <> '')
);

CREATE INDEX idx_tour_api_categories_parent_active
    ON tour_api_categories (parent_code, active);

CREATE INDEX idx_tour_api_categories_depth_active
    ON tour_api_categories (depth, active);

CREATE INDEX idx_places_category
    ON places (category);

COMMENT ON TABLE tour_api_categories IS 'TourAPI 레거시 cat1/cat2/cat3 분류 코드와 공식 자연어 라벨을 관리하는 계층형 매핑 테이블';
COMMENT ON COLUMN tour_api_categories.code IS 'TourAPI 레거시 분류 코드. 대분류 3자리, 중분류 5자리, 소분류 9자리';
COMMENT ON COLUMN tour_api_categories.parent_code IS '상위 분류 코드. 대분류는 NULL, 중분류는 대분류 코드, 소분류는 중분류 코드';
COMMENT ON COLUMN tour_api_categories.depth IS '분류 단계. 1=대분류(cat1), 2=중분류(cat2), 3=소분류(cat3)';
COMMENT ON COLUMN tour_api_categories.name IS 'TourAPI categoryCode2가 제공하는 공식 한국어 분류명';
COMMENT ON COLUMN tour_api_categories.active IS '가장 최근 성공한 전체 동기화에서 확인된 활성 코드 여부';
COMMENT ON COLUMN tour_api_categories.last_seen_sync_token IS '해당 코드를 마지막으로 확인한 전체 동기화 실행 식별자';
COMMENT ON COLUMN tour_api_categories.created_at IS '분류 코드 최초 저장 일시';
COMMENT ON COLUMN tour_api_categories.updated_at IS '분류 코드 또는 라벨 최종 수정 일시';
