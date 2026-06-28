package kr.bi.go_to.model.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 어플리케이션 관점에서의 API 동기화 이력(Sync History)을 관리하는 엔티티
 * 단일 레코드를 덮어쓰지 않고, 매 동기화 실행 시마다 새로운 행을 추가하여 이력을 보존합니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "batch_sync_log")
public class BatchSyncLog {

    /**
     * 동기화 이력 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 동기화를 수행한 배치 작업명 (예: tourApiIncrementalSyncJob)
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * API에 요청한 동기화 기준일자 (예: YYYYMMDD)
     */
    @Column(name = "target_date", nullable = false, length = 20)
    private String targetDate;

    /**
     * 동기화 수행 결과 상태 (SUCCESS / FAIL)
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 해당 일자에 성공적으로 처리된 증분 데이터 건수
     */
    @Column(name = "processed_count", nullable = false)
    @Builder.Default
    private int processedCount = 0;

    /**
     * 동기화 작업이 실행된 실제 시스템 일시
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
