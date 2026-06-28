ALTER TABLE places ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN places.is_deleted IS '해당 장소의 삭제/비공개 여부 (Tour API 동기화 등에서 활용)';

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
COMMENT ON COLUMN batch_sync_log.target_date IS 'API에 요청한 동기화 기준일자 (예: YYYYMMDD)';
COMMENT ON COLUMN batch_sync_log.status IS '동기화 수행 결과 상태 (SUCCESS / FAIL)';
COMMENT ON COLUMN batch_sync_log.processed_count IS '해당 일자에 성공적으로 처리된 증분 데이터 건수';
COMMENT ON COLUMN batch_sync_log.created_at IS '동기화 작업이 실행된 실제 시스템 일시';
