CREATE TABLE etl_failure_log (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE etl_failure_log IS 'ETL 파이프라인에서 처리에 실패한 데이터(DLQ)를 기록하는 로그 테이블';
COMMENT ON COLUMN etl_failure_log.id IS '로그 식별자';
COMMENT ON COLUMN etl_failure_log.external_id IS '실패한 외부 장소 ID (예: contentId)';
COMMENT ON COLUMN etl_failure_log.error_message IS '발생한 Exception의 원인 및 메시지';
COMMENT ON COLUMN etl_failure_log.created_at IS '로그 발생 일시';
