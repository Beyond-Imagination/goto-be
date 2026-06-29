ALTER TABLE places ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN places.is_deleted IS '해당 장소의 삭제/비공개 여부 (Tour API 동기화 등에서 활용)';

ALTER TABLE places ADD COLUMN detail_common_synced BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE places ADD COLUMN detail_with_tour_synced BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE places ADD COLUMN detail_intro_synced BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN places.detail_common_synced IS 'Tour API detailCommon2 상세 보강 성공 여부';
COMMENT ON COLUMN places.detail_with_tour_synced IS 'Tour API detailWithTour2 상세 보강 성공 여부';
COMMENT ON COLUMN places.detail_intro_synced IS 'Tour API detailIntro2 상세 보강 성공 여부';

CREATE TABLE batch_sync_log (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    target_date VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE batch_sync_log IS '어플리케이션 관점에서의 API 동기화 이력(Sync History)을 관리하는 테이블 (단일 레코드가 아닌 이력 누적용)';
COMMENT ON COLUMN batch_sync_log.id IS '동기화 이력 고유 식별자 (PK)';
COMMENT ON COLUMN batch_sync_log.job_name IS '동기화를 수행한 배치 작업명 (예: tourApiIncrementalSyncJob)';
COMMENT ON COLUMN batch_sync_log.target_date IS 'SUCCESS 이력에서는 다음 증분 실행의 modifiedtime 기준일, FAIL 이력에서는 실패 실행의 요청 기준일 (예: YYYYMMDD)';
COMMENT ON COLUMN batch_sync_log.status IS '동기화 수행 결과 상태 (SUCCESS / FAIL)';
COMMENT ON COLUMN batch_sync_log.processed_count IS '증분 base step(tourApiIncrementalBaseSyncStep)의 write count';
COMMENT ON COLUMN batch_sync_log.created_at IS '동기화 작업이 실행된 실제 시스템 일시';
