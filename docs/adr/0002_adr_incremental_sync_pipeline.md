---
author: 강민준 (joonamin44@gmail.com)
date: 2026-06-28
status: Accepted
---

# ADR-0002: Incremental Sync Pipeline and Eager-Lazy Fallback Strategy

## Context

초기 데이터 적재(ADR-0001) 이후, Tour API의 데이터 변경분(추가, 수정, 삭제)을 매일 꾸준히 반영해야 할 필요성이 대두되었습니다. 이를 위해 일회성 수집을 넘어선 증분 동기화(Incremental Sync) 아키텍처를 도입해야 하며, 기존 설계에서 변경된 메타데이터 테이블, 트리거, 삭제 데이터 처리, 상세 조회 전략, 그리고 초기 적재와 증분 스케줄의 실행 순서에 대한 설계 결정이 필요했습니다. 본 문서는 부분적으로 ADR-0001의 내용을 대체(Supersede)합니다.

---

## 1. ETL Scope Expansion (증분 동기화 도입)

### Decision
기존의 일회성 전체 데이터 수집에서 확장하여, `areaBasedSyncList1` API를 활용한 매일 새벽 3시 기준 **증분 동기화 파이프라인**(`tourApiIncrementalSyncJob`)을 구현합니다.

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
- **초기 적재 자동 실행**: `TourApiInitialLoadRunner`가 `ApplicationReadyEvent` 이후 `tourApiInitialLoadJob`을 자동 실행합니다. 단, `goto.batch.initial-load.auto-run-enabled=true`일 때만 자동 실행 기능이 켜집니다.
- **초기 적재 완료 판정**: 초기 적재 여부는 설정값이 아니라 Spring Batch 메타 테이블의 `tourApiInitialLoadJob` 실행 이력 중 `BatchStatus.COMPLETED` 존재 여부로 판단합니다. 설정값은 완료 여부가 아니라 자동 실행 기능을 켜고 끄는 운영 스위치입니다.
- **재시도 정책**: 완료 이력이 없고 실행 중인 초기 적재도 없으면 자동 초기 적재를 시도합니다. 이전 실행이 실패했더라도 `COMPLETED`가 아니면 다음 기동 시 재시도할 수 있습니다.
- **증분 스케줄러 가드**: Spring의 `@Scheduled(cron = "0 0 3 * * ?")`를 활용해 매일 새벽 3시에 증분 동기화를 트리거하되, `tourApiInitialLoadJob`이 아직 완료되지 않았다면 `tourApiIncrementalSyncJob` 실행을 스킵합니다.
- **증분 기준일 메타데이터**: 마지막 성공 동기화 날짜를 명시적으로 추적하기 위한 `batch_sync_log` 커스텀 메타데이터 테이블을 도입합니다.

### Consequences
초기 적재와 증분 동기화의 실행 순서가 애플리케이션 내부에서 보장됩니다. 테스트 프로필에서는 자동 초기 적재를 끌 수 있어 컨텍스트 로딩 테스트가 외부 Tour API를 호출하지 않습니다. Spring Batch 메타 테이블은 초기 적재 완료 여부의 source of truth로 사용하고, `batch_sync_log`는 증분 동기화 기준일을 관리하는 커스텀 운영 메타데이터로 분리합니다.

현재 구현은 `TourApiIncrementalItemReader`가 `batch_sync_log`의 마지막 성공 `target_date`를 조회하는 단계까지 반영되어 있습니다. 성공/실패 결과를 `batch_sync_log`에 기록하는 listener 또는 writer는 아직 구현되지 않았으므로, 증분 기준일 기록 자동화는 후속 작업으로 추적합니다.
