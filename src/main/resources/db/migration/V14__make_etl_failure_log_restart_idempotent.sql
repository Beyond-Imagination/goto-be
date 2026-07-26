ALTER TABLE etl_failure_log
    ADD COLUMN job_instance_id BIGINT,
    ADD COLUMN step_name VARCHAR(100);

CREATE UNIQUE INDEX uk_etl_failure_log_batch_item
    ON etl_failure_log (
        job_instance_id,
        step_name,
        COALESCE(external_id, ''),
        MD5(COALESCE(error_message, ''))
    )
    WHERE job_instance_id IS NOT NULL;

COMMENT ON COLUMN etl_failure_log.job_instance_id IS
    'Spring Batch 재시작 간 동일 실패 로그 중복을 방지하는 JobInstance 식별자';
COMMENT ON COLUMN etl_failure_log.step_name IS
    '동일 JobInstance 안에서 실패가 발생한 step 이름';
