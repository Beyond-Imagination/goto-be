---
author: 강민준 (joonamin44@gmail.com)
date: 2026-06-28
status: Accepted
---

# ADR-0002: Incremental Sync Pipeline and Eager-Lazy Fallback Strategy

## Context

초기 데이터 적재(ADR-0001) 이후, Tour API의 데이터 변경분(추가, 수정, 삭제)을 매일 꾸준히 반영해야 할 필요성이 대두되었습니다. 이를 위해 일회성 수집을 넘어선 증분 동기화(Incremental Sync) 아키텍처를 도입해야 하며, 기존 설계에서 변경된 메타데이터 테이블, 트리거, 삭제 데이터 처리, 그리고 상세 조회 전략에 대한 설계 결정이 필요했습니다. 본 문서는 부분적으로 ADR-0001의 내용을 대체(Supersede)합니다.

---

## 1. ETL Scope Expansion (증분 동기화 도입)

### Decision
기존의 일회성 전체 데이터 수집에서 확장하여, `areaBasedSyncList1` API를 활용한 매일 자정 기준 **증분 동기화 파이프라인**(`tourApiIncrementalSyncJob`)을 구현합니다.

### Consequences
최초 동기화용 Job(`tourApiInitialLoadJob`)과 증분 동기화용 Job(`tourApiIncrementalSyncJob`)이 분리 운영되어 운영 안정성과 효율성이 향상됩니다.

---

## 2. Extract Strategy: Eager-Lazy Fallback (즉각-지연 폴백 조회 전략)

### Decision
증분 수집 시 프로세서 단에서 즉각 상세 조회를 시도(Eager Fetch)하고, 만약 API 요율 제한이나 일시적 오류로 실패할 경우 `overview=null`로 마킹하여 추후의 지연 조회(Lazy Detail Fetch Step)가 잔여 구멍을 채우도록 하는 **하이브리드 전략**을 사용합니다.

### Consequences
가능한 한 데이터를 즉시 완성하되, 대규모 변경 시 발생할 수 있는 Rate Limit 초과에 유연하게 대응할 수 있습니다.

---

## 3. PLACE Table Schema: Soft Delete (논리적 삭제)

### Decision
Tour API의 `showflag=0` (삭제 데이터) 처리를 반영하기 위해 `places` 테이블에 `is_deleted` (Boolean) 컬럼을 추가합니다. 삭제된 장소는 `place_bf_info` 업데이트를 스킵하여 불필요한 리소스 낭비를 막습니다.

### Consequences
물리적 삭제로 인한 외래키 위반 위험을 없애고, 내부 로직 및 앱에서는 논리적 삭제 플래그만 검토하여 노출을 제어할 수 있습니다.

---

## 4. Execution Trigger & Metadata (스케줄러와 메타데이터 테이블)

### Decision
- **스케줄러**: Spring의 `@Scheduled(cron = "0 0 3 * * ?")`를 활용해 매일 새벽 3시에 자동 실행합니다.
- **메타데이터**: 마지막 성공 동기화 날짜를 명시적으로 추적하기 위한 `batch_sync_log` 커스텀 메타데이터 테이블을 도입합니다.

### Consequences
운영자의 수동 개입 없이 파이프라인이 자동화되며, Spring Batch의 복잡한 메타데이터 테이블에 의존하지 않고 직관적인 타겟 날짜 조회가 가능해집니다.
