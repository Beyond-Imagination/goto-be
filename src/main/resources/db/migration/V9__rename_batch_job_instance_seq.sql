-- Spring Batch 6: BATCH_JOB_SEQ -> BATCH_JOB_INSTANCE_SEQ
-- V3가 생성한 batch_job_seq를 Spring Batch 6.0.4 JdbcJobRepository가 기대하는 이름으로 rename
ALTER SEQUENCE batch_job_seq RENAME TO batch_job_instance_seq;
