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
증분 수집 시 프로세서 단에서 즉각 상세 조회를 시도(Eager Fetch)하고, 만약 API 요율 제한이나 일시적 오류로 일부 detail API가 누락될 경우 해당 detail sync flag를 `false`로 남겨 추후의 지연 조회(Lazy Detail Fetch Step)가 잔여 구멍을 채우도록 하는 **하이브리드 전략**을 사용합니다.

상세 보강 완료 상태는 `detailCommon2`, `detailWithTour2`, `detailIntro2`가 모두 성공했을 때만 성립합니다. 이를 위해 `places` 테이블에 `detail_common_synced`, `detail_with_tour_synced`, `detail_intro_synced` flag를 두고, Lazy Detail Fetch Step은 `source = 'TOUR_API'`, `is_deleted = false`이면서 세 flag 중 하나라도 `false`인 장소를 상세 보강 대상으로 삼습니다. `is_deleted=true`인 장소는 Tour API 증분 목록의 `showflag=0`으로 삭제 또는 비공개 처리된 row이므로, detail API 호출 대상에서 제외합니다.

### Consequences
가능한 한 데이터를 즉시 완성하되, 대규모 변경 시 발생할 수 있는 Rate Limit 초과에 유연하게 대응할 수 있습니다.

삭제된 장소를 Lazy Detail Fetch 대상에서 제외하면, 같은 증분 Job 안에서 `showflag=0`으로 삭제 처리한 row가 상세 미완료 상태라는 이유로 다시 상세 보강 대상에 포함되어 `is_deleted=false`로 되돌아가는 회귀를 막을 수 있습니다. 이 정책은 detail step을 "상세 보강 전용"으로 제한하고, 삭제/복구 상태 판단은 `areaBasedSyncList1` 증분 목록의 `showflag`에 위임한다는 의미입니다.

다만 다음 리스크를 운영상 인지해야 합니다.

- `is_deleted=true`가 잘못 저장된 장소는 Lazy Detail Fetch로는 복구되지 않습니다. 해당 장소의 복구는 이후 `areaBasedSyncList1`에서 `showflag=1` 또는 비삭제 상태로 다시 내려와 증분 동기화가 처리해야 합니다.
- 삭제 후 복구 이벤트를 놓치면 `is_deleted=true` 상태가 오래 유지될 수 있습니다. `TourApiIncrementalSyncLogListener`가 성공 이력을 `batch_sync_log`에 기록하므로 정상 실행 간 catch-up 기준은 유지되지만, 로그 기록 자체가 실패하거나 수동으로 이력을 수정하면 증분 기준일이 틀어질 수 있습니다.
- 향후 관리자 화면, 감사 로그, 데이터 품질 분석처럼 삭제된 장소의 상세 정보를 계속 최신화해야 하는 요구가 생기면, 현재 정책과 충돌합니다. 이 경우 앱 노출용 상세 보강과 삭제 row 감사용 상세 수집을 별도 step 또는 별도 저장소로 분리해야 합니다.
- `detailWithTour2`와 `detailIntro2`는 둘 다 성공했을 때만 `place_bf_info.bf_details` JSONB에 반영합니다. `detailWithTour2` 원본 JSON 필드는 root에 직접 저장하지 않고 `PlaceBfDetails` JSON schema의 `mobility`, `visual`, `hearing`, `infant_family` 카테고리로 정규화합니다. `detailIntro2` 응답은 앱에서 바로 쓰는 `intro` projection으로 저장합니다. 두 원천의 원문은 재처리와 디버깅을 위해 `sources.tour_api.detailWithTour`, `sources.tour_api.detailIntro` 아래에도 보존합니다. 둘 중 하나라도 누락되면 `place_bf_info`를 완성 상태로 갱신하지 않고, 다음 Lazy Detail Fetch 대상에 남깁니다.
- `TourApiBfDetailsNormalizer`는 Tour API의 빈 문자열을 "없음"으로 추론하지 않습니다. 원문 설명이 있는 항목만 `is_available=true`로 저장하고, 판별할 수 없는 항목은 `is_available`, `count`, `details`를 모두 `null`로 둡니다. `is_available=false`는 외부 원천이 명시적으로 이용 불가를 표현할 수 있을 때만 사용합니다.
- `places`의 일반 필드는 writer에서 `COALESCE(EXCLUDED.column, places.column)`으로 갱신하므로, 배치 계층에서 `null`은 "이번 처리 경로에서는 아직 모름/갱신하지 않음"을 뜻합니다. 반대로 외부 detail API가 성공했지만 특정 필드 값이 누락된 경우에는 빈 문자열(`""`)을 저장해 오래된 값을 명시적으로 제거합니다. UI는 `""`을 "제공된 정보 없음", `null`을 "현재 데이터 갱신중"과 같이 구분해 표현할 수 있습니다.

---

## 3. PLACE Table Schema: Soft Delete (논리적 삭제)

### Decision
Tour API의 `showflag=0` (삭제 데이터) 처리를 반영하기 위해 `places` 테이블에 `is_deleted` (Boolean) 컬럼을 추가합니다. 삭제된 장소는 `place_bf_info` 업데이트를 스킵하여 불필요한 리소스 낭비를 막습니다.

### Consequences
물리적 삭제로 인한 외래키 위반 위험을 없애고, 내부 로직 및 앱에서는 논리적 삭제 플래그만 검토하여 노출을 제어할 수 있습니다.

`is_deleted`는 detail API 응답으로 판단하지 않습니다. 삭제 상태의 source of truth는 `areaBasedSyncList1`의 `showflag`이며, detail step은 이 상태를 변경하지 않아야 합니다.

---

## 4. Execution Trigger & Metadata (스케줄러와 메타데이터 테이블)

### Decision
- **초기 적재 자동 실행**: `TourApiInitialLoadRunner`가 `ApplicationReadyEvent` 이후 `tourApiInitialLoadJob`을 자동 실행합니다. 단, `goto.batch.initial-load.auto-run-enabled=true`일 때만 자동 실행 기능이 켜집니다.
- **초기 적재 완료 판정**: 초기 적재 여부는 설정값이 아니라 Spring Batch 메타 테이블의 `tourApiInitialLoadJob` 실행 이력 중 `BatchStatus.COMPLETED` 존재 여부로 판단합니다. 설정값은 완료 여부가 아니라 자동 실행 기능을 켜고 끄는 운영 스위치입니다.
- **재시도 정책**: 완료 이력이 없고 실행 중인 초기 적재도 없으면 자동 초기 적재를 시도합니다. 이전 실행이 실패했더라도 `COMPLETED`가 아니면 다음 기동 시 재시도할 수 있습니다.
- **증분 스케줄러 가드**: Spring의 `@Scheduled(cron = "0 0 3 * * ?")`를 활용해 매일 새벽 3시에 증분 동기화를 트리거하되, `tourApiInitialLoadJob`이 아직 완료되지 않았다면 `tourApiIncrementalSyncJob` 실행을 스킵합니다.
- **증분 기준일 메타데이터**: 마지막 성공 동기화 날짜를 명시적으로 추적하기 위한 `batch_sync_log` 커스텀 메타데이터 테이블을 도입합니다. `TourApiIncrementalItemReader`는 마지막 성공 `target_date`를 이번 실행의 API 요청 기준일(`requestDate`)로 사용하고, KST 기준 실행일을 성공 시 저장할 다음 기준일(`targetDate`)로 Job 실행 컨텍스트에 함께 기록합니다. `TourApiIncrementalSyncLogListener`는 Job 종료 후 `SUCCESS` 이력에는 다음 기준일을, `FAIL` 이력에는 실패 실행의 요청 기준일을 `batch_sync_log`에 추가합니다.

### Consequences
초기 적재와 증분 동기화의 실행 순서가 애플리케이션 내부에서 보장됩니다. 테스트 프로필에서는 자동 초기 적재를 끌 수 있어 컨텍스트 로딩 테스트가 외부 Tour API를 호출하지 않습니다. Spring Batch 메타 테이블은 초기 적재 완료 여부의 source of truth로 사용하고, `batch_sync_log`는 증분 동기화 기준일을 관리하는 커스텀 운영 메타데이터로 분리합니다.

현재 구현은 `TourApiIncrementalItemReader`가 `batch_sync_log`의 마지막 성공 `target_date`를 조회하고, `TourApiIncrementalSyncLogListener`가 Job 종료 시점에 실행 결과를 write-back하는 단계까지 반영되어 있습니다. 성공 이력은 다음 실행의 watermark를 전진시키고, 실패 이력은 재시도 진단을 위해 요청 기준일을 남깁니다. `processed_count`는 증분 base step(`tourApiIncrementalBaseSyncStep`)의 write count를 기준으로 기록합니다.
