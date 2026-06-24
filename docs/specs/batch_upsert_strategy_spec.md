# Technical Specification: Batch Upsert Strategy

**Document Status:** Final  
**Author:** 강민준 (joonamin44@gmail.com)  
**Date:** 2026-06-24  

## 1. Project Overview

### 1.1 Purpose
본 문서는 "함께가길" (goto) 프로젝트의 Spring Batch 기반 ETL 파이프라인(Phase 4: Load) 과정에서 대용량 장소(`Place`) 데이터를 효율적이고 안전하게 데이터베이스에 적재(Upsert)하기 위한 세부 구현 전략 및 아키텍처 의사결정을 정의합니다.

### 1.2 Scope
*   **In-Scope:** Spring Batch의 `ItemWriter` 컴포넌트 데이터베이스 적재 로직, PostgreSQL 충돌 해결(Conflict Resolution) 전략, 성능 및 메모리 관리 최적화 방안.
*   **Out-of-Scope:** 데이터 추출(Extract) 및 변환(Transform) 로직, 스케줄링(Quartz/Spring Scheduler) 설정, 애플리케이션 사용자 트래픽 처리 로직.

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

*   **Component:** `kr.bi.go_to.batch.writer.PlaceItemWriter` (`ItemWriter<Place>` 구현체)
*   **Database Syntax:** PostgreSQL `INSERT ... ON CONFLICT (external_id, source) DO UPDATE SET ...`
*   **Spatial Data Handling:** JTS `Point` 객체는 PostGIS의 `ST_GeomFromText(?, 4326)` 내장 함수를 호출하여 안전하게 WKT(Well-Known Text) 포맷에서 지오메트리 객체로 변환 및 적재됩니다.

```sql
INSERT INTO places (external_id, source, category, name, sanitized_address, location_point, thumbnail_url, overview, homepage, tel, content_type_id, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, NOW(), NOW())
ON CONFLICT (external_id, source)
DO UPDATE SET
    category = EXCLUDED.category,
    name = EXCLUDED.name,
    sanitized_address = EXCLUDED.sanitized_address,
    location_point = EXCLUDED.location_point,
    thumbnail_url = EXCLUDED.thumbnail_url,
    overview = EXCLUDED.overview,
    homepage = EXCLUDED.homepage,
    tel = EXCLUDED.tel,
    content_type_id = EXCLUDED.content_type_id,
    updated_at = NOW()
```

---

## 4. Implementation & Operations

### 4.1 Batch Operations
*   `TourApiBatchConfig` 내부에서 Chunk Size는 기본 **100건**으로 설정되어 있으며, `JdbcTemplate.batchUpdate()`를 통해 해당 청크 크기만큼 한 번에 벌크 연산이 발생합니다.

### 4.2 Constraints & Risk Mitigation
*   **Schema Coupling:** Native Query를 사용하므로, 엔티티 스키마(`places` 테이블 컬럼)가 변경될 경우 쿼리 문자열(SQL)도 수동으로 함께 수정해야 하는 강결합(Tight Coupling) 제약이 따릅니다.
*   **Mitigation:** `PlaceItemWriter` 로직에 대한 별도 단위/통합 테스트를 작성하여, 스키마 변경 시 컴파일 단계나 CI 파이프라인에서 쿼리 오류가 조기 발견될 수 있도록 조치합니다.

### 4.3 Alternative Considered
*   **Hibernate `@SQLInsert` / `@SQLUpdate`:** JPA 엔티티 선언부에 직접 네이티브 쿼리를 오버라이드 하는 방식. 
    *   *거절 사유:* 여전히 영속성 컨텍스트(L1 캐시)를 경유하며, 웹 서비스의 비즈니스 로직(JPA)과 배치 로직(Native) 간 격리가 불분명해집니다. 배치를 위한 DB 접근 계층은 독립적인 `JdbcTemplate`으로 분리하는 것이 구조적으로 우수합니다.
