DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM places
        WHERE source <> 'TOUR_API'
          AND category IS NOT NULL
    ) THEN
        RAISE EXCEPTION
            'V13 blocked: non-Tour places must not carry Tour API category values';
    END IF;
END
$$;

ALTER TABLE tour_api_categories
    DROP CONSTRAINT IF EXISTS ck_tour_api_categories_code_hierarchy;

ALTER TABLE tour_api_categories
    DROP CONSTRAINT IF EXISTS fk_tour_api_categories_parent;

ALTER TABLE tour_api_categories
    ALTER COLUMN code TYPE TEXT,
    ALTER COLUMN parent_code TYPE TEXT;

ALTER TABLE tour_api_categories
    ADD CONSTRAINT fk_tour_api_categories_parent
        FOREIGN KEY (parent_code) REFERENCES tour_api_categories (code);

COMMENT ON TABLE tour_api_categories IS
    'Tour API KorService2 최신 메뉴 분류 체계 마스터';
COMMENT ON COLUMN tour_api_categories.code IS
    'lclsSystmCode2 API에서 제공하는 정규 분류 코드 (길이 및 접두사 임의 추정 금지)';
COMMENT ON COLUMN tour_api_categories.parent_code IS
    '수집 튜플에서 재구성한 명시적 상위 분류 코드';
COMMENT ON COLUMN tour_api_categories.depth IS
    '메뉴 분류 단계 (1=대분류, 2=중분류, 3=소분류)';
COMMENT ON COLUMN tour_api_categories.name IS
    'lclsSystmCode2 API에서 제공하는 원본 한글 분류명';

ALTER INDEX IF EXISTS idx_places_category RENAME TO idx_places_category_code;

ALTER TABLE places
    RENAME COLUMN category TO category_code;

ALTER TABLE places
    ALTER COLUMN category_code TYPE TEXT;

ALTER TABLE places
    ADD CONSTRAINT fk_places_tour_api_category
        FOREIGN KEY (category_code) REFERENCES tour_api_categories (code) NOT VALID;

COMMENT ON COLUMN places.category_code IS
    'Tour API 최신 소분류 코드 (lclsSystm3); 대/중분류 계층은 tour_api_categories 테이블 조인으로 해결';
