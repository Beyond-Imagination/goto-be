# Technical Specification: Batch Persistence & Sync Metadata Strategy

**Document Status:** Final  
**Author:** 강민준 (joonamin44@gmail.com)  
**Date:** 2026-06-29  

## 1. Project Overview

### 1.1 Purpose
본 문서는 "함께가길" (goto) 프로젝트의 Spring Batch 기반 ETL 파이프라인에서 장소(`Place`) 데이터 영속화, 상세 보강 상태 저장, 증분 동기화 메타데이터 기록을 효율적이고 안전하게 처리하기 위한 세부 구현 전략 및 아키텍처 의사결정을 정의합니다.

### 1.2 Scope
*   **In-Scope:** Spring Batch의 `ItemWriter` 컴포넌트 데이터베이스 적재 로직, PostgreSQL 충돌 해결(Conflict Resolution) 전략, 상세 보강 flag 및 `place_bf_info` 저장 정책, 증분 동기화 이력(`batch_sync_log`) write-back 트랜잭션 경계, 성능 및 메모리 관리 최적화 방안.
*   **Out-of-Scope:** Tour API 호출 자체의 Extract 흐름, 스케줄링(Quartz/Spring Scheduler) 트리거 정책, 애플리케이션 사용자 트래픽 처리 로직.

---

## 2. Requirements

### 2.1 Functional Requirements
*   기존 데이터베이스에 동일한 식별자(`external_id`, `source`)를 가진 장소가 존재하지 않으면 **신규 삽입(Insert)** 처리되어야 합니다.
*   동일 식별자를 가진 장소가 이미 존재한다면, 변경된 데이터로 **수정(Update)** 처리되어 최신 상태를 유지해야 합니다.

### 2.2 Non-Functional Requirements
*   **Performance (성능):** 수천~수만 건의 데이터 적재 시 N+1 Select 문제를 방지하고, 단일 쿼리 왕복(Round-Trip)으로 대량 처리가 가능해야 합니다.
*   **Isolation (격리성):** 배치 적재 작업이 동일 인스턴스에서 구동 중인 웹 애플리케이션의 메모리(JPA 영속성 컨텍스트, L1/L2 캐시 등)를 오염시키거나 지연(Lag)을 유발해서는 안 됩니다.

---

## 3. System Architecture & Design

### 3.1 Architecture Decision: JDBC Native Query over JPA
대용량 데이터의 Upsert 처리를 위해 Spring Data JPA(`saveAll`) 대신 **Spring JDBC(`JdbcTemplate`) 기반의 Native Query**를 채택합니다.

#### Rationale (채택 사유)
1.  **JPA 방식의 한계:** JPA의 `saveAll()`이나 `findBy...`를 활용할 경우, 각 아이템마다 `SELECT`를 통해 존재 여부를 확인하고 영속성 컨텍스트를 거쳐 `INSERT/UPDATE`를 수행합니다. 이는 Dirty Checking 및 엔티티 스냅샷 생성으로 인한 극심한 CPU/메모리 오버헤드를 유발하며, Hibernate 2차 캐시(L2 Cache) 오염(Thrashing)의 직접적인 원인이 됩니다.
2.  **JDBC의 압도적 성능:** 데이터베이스(PostgreSQL) 레벨의 Native Upsert 문법(`ON CONFLICT`)을 활용하면 JVM 레벨의 맵핑 및 캐싱 로직을 전부 바이패스하여 최고의 성능을 보장할 수 있습니다.

### 3.2 Technical Implementation (세부 구현)

*   **Component:** `kr.bi.go_to.batch.writer.PlaceItemWriter` (`ItemWriter<PlaceProcessingResult>` 구현체)
*   **Database Syntax:** PostgreSQL `INSERT ... ON CONFLICT (external_id, source) DO UPDATE SET ...`
*   **Spatial Data Handling:** JTS `Point` 객체는 PostGIS의 `ST_GeomFromText(?, 4326)` 내장 함수를 호출하여 안전하게 WKT(Well-Known Text) 포맷에서 지오메트리 객체로 변환 및 적재됩니다.
*   **Chunk-level Source Integrity (청크 단위 소스 무결성 검증)**:
    *   배치 실행 성능 향상을 위해 1개의 청크 내에 입력되는 데이터들은 항상 동일한 데이터 소스(`source`)를 가져야만 합니다. `PlaceItemWriter` 진입 시 청크 내 모든 아이템의 소스가 동일한지 확인하며, 서로 다른 소스가 혼재해 있을 경우 `MixedSourceChunkException`을 던져 데이터 꼬임을 방지합니다.
*   **Dynamic Source Selection (동적 소스 바인딩)**:
    *   기본 키 조회를 위한 Select 쿼리에서 하드코딩되었던 `source = 'TOUR_API'` 조건을 `:source` 동적 네임드 파라미터 바인딩으로 대체하였습니다.
    *   이를 통해 `TOUR_API` 외에도 다양한 데이터 소스(예: `KAKAO_API`, `LOCAL_CSV` 등)를 하나의 공통 `PlaceItemWriter`를 통해 동일 트랜잭션 청크 내에서 유연하게 적재 및 업데이트할 수 있게 확장성을 확보했습니다.
*   **Soft Delete Handling (논리 삭제 처리)**:
    *   `places.is_deleted`는 Tour API의 `showflag=0` 삭제 데이터를 반영하기 위한 명시 컬럼입니다. `PlaceItemWriter`는 장소 Upsert 시 `is_deleted`를 항상 `EXCLUDED.is_deleted` 값으로 갱신합니다.
    *   삭제된 장소(`is_deleted=true`)는 `place_bf_info` Upsert 대상에서 제외하여, 삭제 데이터에 대해 무장애 상세 정보를 갱신하지 않습니다.
    *   Lazy Detail Fetch Step은 삭제된 장소를 상세 보강 대상으로 삼지 않습니다. 현재 상세 보강 대상 조회 조건은 `source = 'TOUR_API' AND is_deleted = false`이면서 `detail_common_synced`, `detail_with_tour_synced`, `detail_intro_synced` 중 하나라도 `false`인 row입니다.
    *   삭제/복구 상태 판단은 detail API가 아니라 `areaBasedSyncList2`의 `showflag`를 기준으로 합니다. 따라서 detail step은 `is_deleted` 상태를 복구하거나 변경하는 책임을 갖지 않습니다.
*   **Detail Completion Flags (상세 보강 완료 상태)**:
    *   상세 보강 완료는 `detailCommon2`, `detailWithTour2`, `detailIntro2` 세 API가 모두 성공했을 때만 성립합니다.
    *   `places.detail_common_synced`, `places.detail_with_tour_synced`, `places.detail_intro_synced`는 각 API의 성공 여부를 저장합니다.
    *   `detailWithTour2`와 `detailIntro2`가 모두 성공한 경우에만 `place_bf_info.bf_details`를 갱신합니다. `detailWithTour2` 응답은 `TourApiBfDetailsNormalizer`를 통해 `PlaceBfDetails` 스키마로 정규화하고, `detailIntro2` 응답은 앱에서 바로 쓰는 `intro` projection으로 저장합니다. 두 원천의 원문은 `sources.tour_api.detailWithTour`, `sources.tour_api.detailIntro`에도 보존합니다.
*   **Barrier-free JSON Schema (bf_details 저장 스키마)**:
    *   `bf_details`의 최상위 key는 항상 `mobility`, `visual`, `hearing`, `infant_family`, `intro`, `sources`입니다.
    *   `mobility`, `visual`, `hearing`, `infant_family` 내부에는 현재 Tour API에서 매핑 대상으로 삼는 알려진 편의시설 field를 모두 생성합니다. 원본 응답에 값이 비어 있더라도 field 자체는 생략하지 않습니다.
    *   각 편의시설 field의 값은 `{ "is_available": Boolean|null, "count": Integer|null, "details": String|null }` 형태입니다.
    *   원본 설명이 있는 경우에만 `is_available=true`로 저장합니다. `count`는 원문 괄호 안 숫자(예: `(9대)`)를 추출할 수 있을 때만 저장하고, `details`는 원문 설명을 저장합니다.
    *   원본 값이 없거나 빈 문자열이면 `is_available`, `count`, `details`를 모두 `null`로 둡니다. 이는 "없음"이 아니라 "외부 데이터만으로 판별 불가"를 의미합니다. 현재 Tour API 응답만으로는 명시적인 `false`를 만들지 않습니다.
    *   `parking`, `exit`, `restroom` 같은 Tour API raw field는 `bf_details` top-level에 저장하지 않습니다.
    *   `intro`는 앱에서 바로 쓰는 현재 projection입니다. Tour API 원문은 `sources.tour_api.detailWithTour`, `sources.tour_api.detailIntro` 아래에도 보존하여 재처리와 디버깅 근거로 사용합니다.

```json
{
  "mobility": {
    "parking": {
      "is_available": true,
      "count": 9,
      "details": "장애인 전용 주차구역 있음(9대)_무장애 편의시설"
    },
    "elevator": {
      "is_available": null,
      "count": null,
      "details": null
    }
  },
  "visual": {
    "braileblock": {
      "is_available": null,
      "count": null,
      "details": null
    }
  },
  "hearing": {
    "signguide": {
      "is_available": null,
      "count": null,
      "details": null
    }
  },
  "infant_family": {
    "lactationroom": {
      "is_available": true,
      "count": null,
      "details": "수유실 있음(관리사무실)"
    }
  },
  "intro": {
    "contentid": "1067369",
    "usetime": "09:00~18:00"
  },
  "sources": {
    "tour_api": {
      "externalId": "1067369",
      "externalSubId": null,
      "evalInfo": null,
      "syncedAt": "2026-07-01T00:00:00Z",
      "detailWithTour": {
        "contentid": "1067369",
        "parking": "장애인 전용 주차구역 있음(9대)_무장애 편의시설",
        "elevator": ""
      },
      "detailIntro": {
        "contentid": "1067369",
        "usetime": "09:00~18:00"
      }
    }
  }
}
```
*   **Null-preserving Update (부분 상세 보강 안전성)**:
    *   상세 보강 Step은 일부 필드를 `null`로 전달할 수 있으므로, `places` 갱신 시 대부분의 일반 필드는 `COALESCE(EXCLUDED.column, places.column)`으로 기존 값을 보존합니다. 단, `is_deleted`는 삭제 상태가 명시적으로 반영되어야 하므로 기존 값을 보존하지 않습니다.
    *   배치 계층에서 `null`은 "이번 처리 경로에서는 이 필드를 아직 모른다/갱신하지 않는다"는 의미입니다. DB의 기존 값은 유지됩니다.
    *   빈 문자열(`""`)은 "외부 API 응답은 성공했지만 해당 필드에 제공된 값이 없다"는 의미입니다. `COALESCE`는 빈 문자열을 `NULL`로 보지 않으므로 기존 값을 빈 문자열로 덮어써 stale 값을 제거합니다.
    *   UI는 `""`을 "제공된 정보 없음", `null`을 "현재 데이터 갱신중"과 같이 구분해 표현할 수 있습니다.
    *   detail API 호출 자체가 실패한 경우에는 관련 필드를 `null`로 유지하고, detail API가 성공했으나 개별 필드가 누락/빈 값인 경우에만 `""`로 매핑합니다.

```sql
-- Place 벌크 Upsert 쿼리
INSERT INTO places (external_id, source, category, name, sanitized_address, location_point, thumbnail_url, overview, homepage, tel, content_type_id, is_deleted, detail_common_synced, detail_with_tour_synced, detail_intro_synced, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
ON CONFLICT (external_id, source)
DO UPDATE SET
    category = COALESCE(EXCLUDED.category, places.category),
    name = COALESCE(EXCLUDED.name, places.name),
    sanitized_address = COALESCE(EXCLUDED.sanitized_address, places.sanitized_address),
    location_point = COALESCE(EXCLUDED.location_point, places.location_point),
    thumbnail_url = COALESCE(EXCLUDED.thumbnail_url, places.thumbnail_url),
    overview = COALESCE(EXCLUDED.overview, places.overview),
    homepage = COALESCE(EXCLUDED.homepage, places.homepage),
    tel = COALESCE(EXCLUDED.tel, places.tel),
    content_type_id = COALESCE(EXCLUDED.content_type_id, places.content_type_id),
    is_deleted = EXCLUDED.is_deleted,
    detail_common_synced = EXCLUDED.detail_common_synced,
    detail_with_tour_synced = EXCLUDED.detail_with_tour_synced,
    detail_intro_synced = EXCLUDED.detail_intro_synced,
    updated_at = NOW();

-- ID 매핑 조회를 위한 동적 Source SQL
SELECT id, external_id FROM places WHERE external_id IN (:externalIds) AND source = :source;

-- Lazy Detail Fetch 대상 조회
SELECT external_id, source, category, name, sanitized_address, location_point, thumbnail_url, content_type_id, tel
FROM places
WHERE source = 'TOUR_API'
  AND is_deleted = false
  AND (
      detail_common_synced = false
      OR detail_with_tour_synced = false
      OR detail_intro_synced = false
  )
LIMIT ?;

-- 무장애 상세 정보 Upsert 쿼리
INSERT INTO place_bf_info (place_id, bf_details, last_synced_at, created_at, updated_at)
VALUES (?, ?::jsonb, NOW(), NOW(), NOW())
ON CONFLICT (place_id)
DO UPDATE SET
    bf_details = EXCLUDED.bf_details,
    last_synced_at = EXCLUDED.last_synced_at,
    updated_at = NOW();
```

---

## 4. Implementation & Operations

### 4.1 Batch Operations
*   `TourApiBatchConfig` 내부에서 Chunk Size는 기본 **100건**으로 설정되어 있으며, `JdbcTemplate.batchUpdate()`를 통해 해당 청크 크기만큼 한 번에 벌크 연산이 발생합니다.

### 4.2 Constraints & Risk Mitigation
*   **Schema Coupling:** Native Query를 사용하므로, 엔티티 스키마(`places` 테이블 컬럼)가 변경될 경우 쿼리 문자열(SQL)도 수동으로 함께 수정해야 하는 강결합(Tight Coupling) 제약이 따릅니다.
*   **Mitigation:** `PlaceItemWriter` 로직에 대한 별도 단위/통합 테스트를 작성하여, 스키마 변경 시 컴파일 단계나 CI 파이프라인에서 쿼리 오류가 조기 발견될 수 있도록 조치합니다.
*   **Soft-delete Detail Coupling:** `is_deleted=true`인 장소는 Lazy Detail Fetch 대상에서 제외됩니다. 잘못 삭제 처리된 row는 detail step으로 복구되지 않으며, 이후 증분 목록에서 비삭제 `showflag`가 내려와야 복구됩니다.
*   **Sync Catch-up Dependency:** 삭제 후 복구 이벤트를 놓치면 삭제 상태가 장시간 유지될 수 있습니다. 현재는 `TourApiIncrementalSyncLogListener`가 성공/실패 이력을 `batch_sync_log`에 write-back하므로 정상 실행 간 기준일은 유지됩니다. 성공 이력의 `target_date`는 다음 실행의 `modifiedtime` watermark로 전진하고, 실패 이력의 `target_date`는 실패 실행이 요청한 기준일을 남깁니다. `processed_count`는 전체 Job 처리량이 아니라 증분 base step(`tourApiIncrementalBaseSyncStep`)의 write count입니다. 단, 로그 write-back 실패나 수동 이력 수정이 발생하면 증분 기준일이 틀어질 수 있습니다.
*   **Sync Log Transaction Boundary:** `batch_sync_log` write-back은 배치 step의 chunk 트랜잭션과 별도로 커밋되어야 합니다. `BatchSyncLogWriter`는 `REQUIRES_NEW` 트랜잭션으로 실행되며, 외부 트랜잭션이 롤백되더라도 동기화 이력은 독립적으로 남아야 합니다.
*   **Future Admin/Audit Requirements:** 삭제된 장소의 상세 정보를 관리자나 감사 목적으로 계속 최신화해야 한다면, 현재 앱 노출용 detail step과 별도의 삭제 row 수집 정책을 도입해야 합니다.
*   **Detail Completion Coupling:** 상세 보강 완료 상태는 세 API flag와 `place_bf_info.bf_details` 저장 정책에 함께 의존합니다. 향후 detail API가 추가되면 flag, Lazy Detail Fetch 조건, JSONB 저장 구조를 함께 갱신해야 합니다.

### 4.3 Alternative Considered
*   **Hibernate `@SQLInsert` / `@SQLUpdate`:** JPA 엔티티 선언부에 직접 네이티브 쿼리를 오버라이드 하는 방식. 
    *   *거절 사유:* 여전히 영속성 컨텍스트(L1 캐시)를 경유하며, 웹 서비스의 비즈니스 로직(JPA)과 배치 로직(Native) 간 격리가 불분명해집니다. 배치를 위한 DB 접근 계층은 독립적인 `JdbcTemplate`으로 분리하는 것이 구조적으로 우수합니다.
