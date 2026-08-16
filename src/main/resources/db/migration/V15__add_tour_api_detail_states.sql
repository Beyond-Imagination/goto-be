ALTER TABLE places
    ADD COLUMN category_resolution_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN detail_common_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN detail_with_tour_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN detail_intro_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN detail_with_tour_payload JSONB,
    ADD COLUMN detail_intro_payload JSONB;

UPDATE places AS place
SET detail_with_tour_payload = bf_info.bf_details #> '{sources,tour_api,detailWithTour}',
    detail_intro_payload = bf_info.bf_details #> '{sources,tour_api,detailIntro}'
FROM place_bf_info AS bf_info
WHERE bf_info.place_id = place.id;

UPDATE places
SET category_resolution_status = CASE
        WHEN category_code IS NULL OR BTRIM(category_code) = '' THEN 'PENDING'
        ELSE 'RESOLVED'
    END,
    detail_common_status = CASE WHEN detail_common_synced THEN 'SUCCESS' ELSE 'PENDING' END,
    detail_with_tour_status = CASE
        WHEN source = 'TOUR_API' AND detail_with_tour_payload IS NOT NULL THEN 'SUCCESS'
        WHEN source <> 'TOUR_API' AND detail_with_tour_synced THEN 'SUCCESS'
        ELSE 'PENDING'
    END,
    detail_intro_status = CASE
        WHEN source = 'TOUR_API' AND detail_intro_payload IS NOT NULL THEN 'SUCCESS'
        WHEN source <> 'TOUR_API' AND detail_intro_synced THEN 'SUCCESS'
        ELSE 'PENDING'
    END,
    detail_with_tour_synced = CASE
        WHEN source = 'TOUR_API' THEN detail_with_tour_payload IS NOT NULL
        ELSE detail_with_tour_synced
    END,
    detail_intro_synced = CASE
        WHEN source = 'TOUR_API' THEN detail_intro_payload IS NOT NULL
        ELSE detail_intro_synced
    END;

ALTER TABLE places
    ADD CONSTRAINT ck_places_category_resolution_status
        CHECK (category_resolution_status IN ('PENDING', 'RESOLVED', 'NOT_FOUND')),
    ADD CONSTRAINT ck_places_detail_common_status
        CHECK (detail_common_status IN ('PENDING', 'SUCCESS', 'NOT_FOUND', 'SKIPPED')),
    ADD CONSTRAINT ck_places_detail_with_tour_status
        CHECK (detail_with_tour_status IN ('PENDING', 'SUCCESS', 'NOT_FOUND', 'SKIPPED')),
    ADD CONSTRAINT ck_places_detail_intro_status
        CHECK (detail_intro_status IN ('PENDING', 'SUCCESS', 'NOT_FOUND', 'SKIPPED'));

COMMENT ON COLUMN places.category_resolution_status IS
    '현재 Tour API 소분류 복구 상태 (PENDING / RESOLVED / NOT_FOUND)';
COMMENT ON COLUMN places.detail_common_status IS
    'detailCommon2 수집 상태 (PENDING / SUCCESS / NOT_FOUND / SKIPPED)';
COMMENT ON COLUMN places.detail_with_tour_status IS
    'detailWithTour2 수집 상태 (PENDING / SUCCESS / NOT_FOUND / SKIPPED)';
COMMENT ON COLUMN places.detail_intro_status IS
    'detailIntro2 수집 상태 (PENDING / SUCCESS / NOT_FOUND / SKIPPED)';
COMMENT ON COLUMN places.detail_with_tour_payload IS
    '서로 다른 batch 실행에서 detailWithTour2/detailIntro2가 성공할 때 병합하기 위한 detailWithTour2 원문';
COMMENT ON COLUMN places.detail_intro_payload IS
    '서로 다른 batch 실행에서 detailWithTour2/detailIntro2가 성공할 때 병합하기 위한 detailIntro2 원문';
