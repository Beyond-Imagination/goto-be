---
author: 강민준 (joonamin44@gmail.com)
date: 2026-06-23
status: Accepted
---

# ADR-0001: ETL Pipeline Architecture Design for Barrier-Free Tourism Data

## Context

"함께가길" (goto) 프로젝트는 교통약자를 위한 무장애 관광 정보를 제공하기 위해 한국관광공사(KNTO)의 Open API 데이터를 수집하여 자체 데이터 모델(`PLACE`, `PLACE_BF_INFO` 등)로 정제 및 적재하는 ETL(Extract, Transform, Load) 파이프라인을 구축해야 합니다.
이를 위해 데이터 소스의 제약 사항, API 요율 제한, 데이터 정합성, 시스템 복잡도, 유지보수 용이성 및 데이터 최신성 등을 고려하여 파이프라인의 전반적인 기술 스택과 상세 구현 방식을 결정해야 합니다.

---

## 1. ETL Scope (최초 데이터 적재 범위)

### Context & Alternatives

매번 전체 데이터를 실시간 동기화할 것인지, 혹은 일회성/최초 적재(Initial Load)를 우선으로 할 것인지에 대한 결정이 필요했습니다. 실시간 동기화는 높은 API 요청 비용 및 데이터 모델 변경에 따른 복잡도를 야기할 수 있습니다.

### Decision

* **Initial Load Only**: 우선 2026 관광데이터활용 공모전 범위 내에서는 **최초 데이터 적재**를 목표로 합니다.
* 향후 배치 주기나 증분(Incremental) 동기화는 `areaBasedSyncList2` API를 이용해 변경 사항만 수집하는 형태로 확장 가능하도록 설계하되, 현재 단계에서는 안정적인 전체/초기 데이터 마이그레이션에 집중합니다.

### Consequences

* 수집 속도 및 API 호출 최적화에 설계 집중 가능.
* 데이터 적재 시 중복 입력을 방지하기 위한 안전장치(Upsert)가 필수로 요구됨.

---

## 2. Target API Scope (수집 대상 API 선정)

### Context & Alternatives

한국관광공사에서 제공하는 API 중 무장애 여행 정보(KorWithService2), 국문 관광정보 서비스(KorService2), 그리고 무장애관광 정보 서비스(SSIS)가 후보군이었습니다.

* **SSIS API**: 오직 XML만 제공하고 데이터 구조가 다르며 연동 복잡성이 높음.
* **KorWithService2**: 무장애 관련 핵심 데이터와 JSON 형식 지원.
* **KorService2**: 풍부한 일반 관광 정보 및 이미지, JSON 형식 지원.

### Decision

* **KorWithService2 (무장애) + KorService2 (국문)**의 조합을 채택하고, SSIS API는 연동 대상에서 배제합니다.
* JSON 포맷(`_type=json`)을 요청 파라미터로 명시하여 연동합니다.

### Consequences

* 모든 API 응답을 JSON으로 통일하여 파싱 및 DTO 모델링이 단순해짐.
* 무장애 정보와 일반 관광 정보 간의 데이터 연계를 위한 키 맵핑 구조가 필요함.

---

## 3. Extract Strategy (수집 플로우 및 Staged Fetch)

### Context & Alternatives

관광지 리스트를 가져오는 API(`areaBasedList2`)는 요약본 정보만 포함하고 있으므로, 개요, 무장애 세부 편의 정보, 소개 및 상세 이미지를 가져오려면 추가적인 상세 API 호출이 필수적입니다.

* **대안 A**: 리스트 API 결과만 파싱하여 간단하게 적재 (무장애 세부 정보 부족).
* **대안 B (채택)**: Staged Fetch 기법 적용. 리스트 API로 `contentId` 목록을 추출한 뒤, 각 `contentId`별로 상세 API를 순차적/병렬적으로 호출하여 데이터를 보강(Enrichment)함.

### Decision

* **Staged Fetch** 방식을 채택합니다.
  1. `areaBasedList2`를 호출하여 수집할 관광지 목록(`contentId`, `contentTypeId` 등) 확보.
  2. 획득한 각 `contentId` 마다 상세 API들을 호출하여 가공 데이터를 결합(Transform)한 후 최종 데이터베이스에 Load.

### Consequences

* 개별 관광지마다 여러 번의 API 호출이 발생하므로 네트워크 I/O 병목이 발생할 수 있음.
* 이를 해결하기 위해 Spring Batch의 Chunk 지향 프로세싱 및 적절한 I/O 성능 튜닝이 요구됨.

---

## 4. List Source (수집 대상 목록 기준 API)

### Context & Alternatives

목록을 가져올 때 무장애 API의 `areaBasedList2`와 국문 API의 `areaBasedList2` 중 무엇을 모체(기준)로 삼을 것인지에 대한 결정입니다.

* **대안 A**: 양쪽의 목록을 모두 조회하여 병합 및 중복 제거 (대규모 데이터 매핑 오버헤드).
* **대안 B (채택)**: 무장애 여행 정보 API의 `areaBasedList2` 결과만 수집 기준으로 삼고, 국문 API는 무장애 목록에 존재하는 `contentId`에 대한 상세 정보(Enrichment) 보완 목적으로만 호출.

### Decision

* **무장애 여행 정보 API의 목록을 절대적 수집 기준**으로 삼습니다. 국문 API는 해당 목록의 `contentId`에 대한 추가 텍스트 및 상세 데이터 조회를 위해서만 활용됩니다.

### Consequences

* 무장애 정보가 아예 존재하지 않는 일반 관광 정보는 파이프라인에서 자동 배제되므로, 앱의 목적에 부합하는 고품질 무장애 관광 데이터만 타깃팅하여 적재 가능.

---

## 5. Detail API Scope (상세 API 호출 범위)

### Context & Alternatives

`contentId`를 획득한 이후, 정확히 어떤 상세 API들을 연동하여 데이터를 완성할지 결정해야 합니다.

### Decision

* 아래의 **4가지 상세 API**를 모두 호출하여 정보를 조합합니다.
  1. `detailCommon2`: 기본 정보(장소명, 주소, 좌표, 개요, 홈페이지 등)
  2. `detailWithTour2`: 무장애 상세 편의 시설 정보 (휠체어, 점자블록, 안내견 등)
  3. `detailIntro2`: 소개 정보 (이용 시간, 휴무일, 주차 요금 등)
  4. `detailImage2`: 이미지 정보 (대표 썸네일 경로 및 상세 이미지 리스트 검증)

### Consequences

* 하나의 장소를 최종 적재하기 위해 최대 4~5회의 API 호출이 수반되므로 API 호출 제한량(Traffic Limit)에 걸리지 않도록 주의해야 하며, 실패율을 낮추기 위한 재시도 메커니즘이 수반되어야 함.

---

## 6. PLACE Table Schema Extension (데이터 모델 변경)

### Context & Alternatives

기존 `PLACE` 테이블은 `name`, `sanitized_address`, `location_point`, `thumbnail_url` 등 최소한의 컬럼만 가지고 있었습니다. 하지만 OpenAPI에서 제공하는 개요(Overview), 홈페이지 주소, 전화번호, 콘텐츠 타입 등의 데이터를 활용하기 위해 스키마 변경이 필요했습니다.

### Decision

* `PLACE` 테이블에 다음 **역정규화 컬럼들을 추가**합니다.
  * `overview` (TEXT): 관광지 상세 설명/개요
  * `homepage` (VARCHAR): 홈페이지 URL
  * `tel` (VARCHAR): 대표 전화번호
  * `content_type_id` (VARCHAR): 관광지 유형 ID (예: 12-관광지, 32-숙박, 39-음식점 등)

### Consequences

* 상세 화면 렌더링 시 추가 테이블 조인 없이 `PLACE` 단일 조회로 처리 가능하여 조회 성능 향상.
* 스키마 변경 사항은 Flyway 마이그레이션 스크립트에 반영되어야 함.

---

## 7. Detail Image Storage Strategy (상세 이미지 저장 방식)

### Context & Alternatives

`detailImage2` API를 통해 반환되는 여러 장의 상세 이미지 URL들을 DB 내 별도 테이블(예: `PLACE_IMAGE`)로 관리할 것인지에 대한 여부입니다.

* **대안 A**: 상세 이미지 테이블을 신설하여 DB에 전체 적재 (저장소 비용 증가, 동기화 비용 높음).
* **대안 B (채택)**: `PLACE` 테이블에는 대표 이미지(`thumbnail_url`)만 수집하여 저장하고, 상세 슬라이드 이미지 리스트는 DB에 저장하지 않고 클라이언트가 필요 시 API 서버를 통해 직접 또는 캐싱 서버를 통해 실시간 바이패스로 호출하도록 설계.

### Decision

* **대안 B를 채택**하여 DB 저장 용량과 스키마 복잡도를 최소화합니다.

### Consequences

* DB 구조가 단순화되고 적재 프로세스 속도가 향상됨.
* 클라이언트는 상세 이미지가 필요한 시점에 백엔드 API 서버의 바이패스 엔드포인트를 호출하거나 직접 KNTO API로 요청해야 함.

---

## 8. detailIntro2 Storage (소개 정보의 저장)

### Context & Alternatives

`detailIntro2`에서 반환되는 데이터(이용시간, 휴무일, 주차시설 등)는 정형화하기 어려운 필드가 많아 관계형 컬럼으로 설계 시 비효율적입니다.

### Decision

* `PLACE_BF_INFO` 테이블의 **`bf_details` (JSONB) 컬럼 내에 "intro" 또는 "visit_info" 등의 새로운 JSON 카테고리를 할당하여 통합 저장**합니다.

### Consequences

* 복잡하고 가변적인 소개 정보 속성들을 유연하게 수집 및 변경할 수 있음.
* JSONB 인덱스를 활용하여 필요시 빠르게 조회 가능.

---

## 9. Execution Trigger (실행 방식)

### Context & Alternatives

ETL 파이프라인의 실행을 트리거하는 메커니즘을 결정해야 합니다.

* **대안 A**: 완전한 외부 크론탭(Crontab) 또는 인프라 레벨의 Airflow 연동 (인프라 구성 복잡도 상승).
* **대안 B (채택)**: Spring Boot 프레임워크 내장 `Spring Scheduler (@Scheduled)` 기반 실행.

### Decision

* 애플리케이션 개발 편의성과 단일 아티팩트 배포 관리를 위해 **`Spring Scheduler`**를 사용하여 일회성 초기 적재 트리거(또는 프로퍼티 제어를 통한 수동 트리거) 및 정기 점검 배치를 구동합니다.

### Consequences

* 외부 인프라 의존성 없이 Spring Boot 애플리케이션만 배포하면 ETL 배치가 즉시 작동함.
* 스케줄러가 여러 서버 인스턴스에서 동시 실행되어 중복 적재가 발생하는 것을 막기 위해 프로퍼티 제어나 ShedLock 같은 간단한 분산 락 라이브러리를 향후 고려할 수 있음.

---

## 10. Load Strategy - Upsert (적재 전략)

### Context & Alternatives

데이터 적재 시 동일한 장소가 이미 DB에 존재하는 경우, 에러를 던지며 멈출 것인지(Insert Only) 아니면 갱신할 것인지(Upsert) 결정해야 합니다.

### Decision

* **Upsert 전략**을 채택합니다.
* `PLACE` 테이블에 `(external_id, source)` 복합 유니크 제약 조건을 걸고, 이를 기반으로 **`INSERT ON CONFLICT UPDATE`** SQL 쿼리를 실행하여 최신 정보로 덮어씁니다.

### Consequences

* 배치를 중단 없이 재실행해도 데이터의 중복 적재나 PK 충돌 없이 항상 안전하게 최종 상태를 동기화할 수 있음.

---

## 11. HTTP Client & Data Object Modeling (연동 기술)

### Context & Alternatives

외부 API 연동을 위한 클라이언트 기술 스택 선정입니다.

* **대안 A**: 기존 RestTemplate 사용 (동기식이지만 Spring 6 이후 유지보수 모드).
* **대안 B (채택)**: Spring 6에서 도입된 `RestClient` 사용. 동기식 선언적 API 및 현대적 API 스타일 지원.
* **대안 C**: WebClient 사용 (`spring-boot-starter-webflux` 라이브러리 추가 필요, 리액티브 오버헤드 존재).

### Decision

* 현대적인 동기식 HTTP 클라이언트인 **`RestClient`**를 사용하고, API 응답 데이터를 매핑하기 위해 **Java 21 Record DTO** 구조를 채택합니다.

### Consequences

* 가독성 높은 Fluent API 스타일의 코드 작성 가능.
* 별도의 WebFlux 의존성 추가 없이 JVM 최적화된 Record DTO로 응답 데이터 파싱 처리.

---

## 12. API Key Management (보안 및 환경 설정)

### Context & Alternatives

OpenAPI 접근을 위한 공공데이터포털 서비스 키(인증키)를 관리하는 방안입니다. 코드 내 하드코딩은 유출 위험이 높습니다.

### Decision

* **AWS Parameter Store**를 활용하여 암호화된 API 키(`/goto/knto-api-key`)를 보관합니다.
* Spring Cloud AWS Parameter Store Integration을 통하여 애플리케이션 구동 시 `application.yaml` 내부 프로퍼티로 자동 주입되도록 구성합니다.

### Consequences

* 소스 코드 저장소에 인증 키가 노출될 우려가 전혀 없으며, 환경별(Local, Dev, Prod) 키 분리가 용이함.

---

## 13. Error Handling & DLQ Pattern (장애 대응)

### Context & Alternatives

특정 `contentId`에 대한 API 상세 조회가 간헐적으로 네트워크 순단이나 포맷 에러 등으로 실패할 경우, 전체 배치를 롤백하거나 실패할 것인지에 대한 정책 설정입니다.

### Decision

* **DLQ (Dead Letter Queue) 테이블 패턴**을 적용하되, 에러 성격에 따른 분기 정책과 트랜잭션 전파 격리를 보장합니다.
* **관심사 분리 및 트랜잭션 격리**: 에러 기록을 위해 전용 `EtlFailureLogger` 컴포넌트를 정의하고, 해당 로깅 메서드에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 전파 속성을 적용합니다. 이를 통해 본 배치 트랜잭션의 롤백 발생 여부와 상관없이 실패 기록은 항상 안정적으로 커밋(Commit)되도록 보장합니다.
* **오류 유형별 세부 핸들링**:
  * **좌표 형식 및 범위 에러**: 위경도 정보 오류는 경미한 오류로 간주하여, 데이터 수집 자체를 취소하기보다는 좌표 데이터(`location_point`)만 `null`로 비운 채 Place 데이터를 적재합니다. 동시에 `EtlFailureLogger`를 사용해 데이터베이스에 즉시 `COORDINATE_FORMAT_ERROR` / `COORDINATE_OUT_OF_BOUNDS` 에러 로그를 남겨 추후 사후 분석이 가능하게 합니다.
  * **홈페이지 파싱 에러**: 불완전하거나 비정상적인 데이터가 사용자에게 노출되는 것을 원천 차단하기 위해, 여러 개의 URL이 섞여있거나 포맷이 정제되지 않는 홈페이지 에러 시에는 `IllegalArgumentException`을 발생시킵니다. 이 예외는 Spring Batch의 표준 Skip 메커니즘을 타게 되며, `TourApiSkipListener`를 통해 `etl_failure_log`에 스킵 정보가 자동으로 기록되고 해당 아이템 수집은 완전히 Skip됩니다.

### Consequences

* 수만 건의 데이터 적재 시 단 한 건의 에러로 인해 전체 파이프라인이 중단되는 현상을 방지함.
* 에러 로그 유실 가능성을 차단하고(`REQUIRES_NEW`), 비즈니스 영향도에 따라 '완화(Null 채우기 후 적재)'와 '엄격한 격리(Skip)' 정책을 선택적으로 혼용하여 데이터 안정성을 극대화함.
* 관리자가 실패 로그를 모니터링하고 추후 부분 재실행할 수 있는 추적성을 확보함.

---

## 14. Framework Selection (배치 프레임워크)

### Context & Alternatives

단순 루프문 처리와 배치 프레임워크 도입 간의 트레이드오프입니다.

* **대안 A**: 단순 반복문 및 병렬 스트림 구현 (빠르게 개발 가능하나 실패 복구, 트랜잭션 분할, 청크 처리, 모니터링 기능을 수동 구현해야 함).
* **대안 B (채택)**: `Spring Batch` 프레임워크 사용. 대용량 데이터 처리에 검증된 Reader-Processor-Writer 패턴 및 트랜잭션 제어, 청크 처리(Chunk-oriented processing)를 제공함.

### Decision

* 대용량 배치 처리의 안정성 확보 및 검증된 구조적 설계를 위해 **Spring Batch (spring-boot-starter-batch)**를 전격 도입합니다.

### Consequences

* 오류 시 롤백 범위 지정, 청크 단위 트랜잭션 커밋, 재시도(Retry) 및 건너뛰기(Skip) 설정을 선언적으로 활용할 수 있어 아키텍처의 안정성이 크게 제고됨.

---

## 15. Batch Metadata Table Management (메타데이터 스키마)

### Context & Alternatives

Spring Batch 프레임워크는 Job과 Step의 상태 관리를 위해 자체적인 메타데이터 테이블(BATCH_JOB_INSTANCE, BATCH_STEP_EXECUTION 등)을 필요로 합니다.

* **대안 A**: 인메모리 DB(H2)에 메타데이터 저장 (배치 인스턴스 재시작 시 상태 유실되어 실패 지점부터 재시작 불가).
* **대안 B (채택)**: 로컬/운영의 PostgreSQL DB 내에 메타데이터 테이블을 물리적으로 관리.

### Decision

* **로컬 PostgreSQL DB 내에 배치 메타데이터 테이블을 생성**하고, Flyway 마이그레이션 스크립트를 통해 이를 제어하고 관리합니다.
* **Timestamp 정밀도 사양 통일**: PostgreSQL의 `TIMESTAMP` 타입은 최대 6자리(Microseconds)의 소수점 이하 초 정밀도를 지원합니다. 외부 데이터 소스의 정밀도에 맞추어 불필요하게 9자리(`TIMESTAMP(9)`)로 선언 시 생성 오류를 유발하거나 드라이버 호환성 문제가 생길 수 있으므로, 메타데이터 테이블 정의 시 타임스탬프 필드 정밀도를 `TIMESTAMP(6)`으로 통일하여 마이그레이션 스크립트에 반영합니다.

### Consequences

* 배치가 중단되었을 때 정확히 중단된 Step부터 재시작(Restartability)할 수 있어 대용량 배치 실행 안정성 확보.
* PostgreSQL 데이터베이스의 스펙 한계에 맞는 정확한 데이터 타입을 지정하여, 스키마 마이그레이션 및 쿼리 파싱 과정에서의 예기치 못한 데이터 유실이나 런타임 오류 방지.

---

## 16. Spatial Coordinates Transformation (공간 좌표 변환)

### Context & Alternatives

기본 API에서 제공하는 위도(mapy)와 경도(mapx) 좌표 데이터를 PostgreSQL `PLACE` 테이블의 `location_point` (PostGIS `geometry`) 컬럼으로 마이그레이션하기 위한 방법 결정입니다.
또한, 실내 편의시설 노드(`facility_nodes.geojson_point`)의 공간 참조(SRID)를 무엇으로 할지에 대한 논의도 포함합니다.

### Decision

* 데이터를 DB에 로드하는 시점에 **PostGIS의 `ST_SetSRID(ST_MakePoint(lng, lat), 4326)`** 함수를 실행하여 경위도 좌표계를 EPSG:4326(WGS84) 공간 좌표 타입으로 정제하여 적재합니다.
* **실내 도면 노드(facility_nodes)의 SRID도 4326으로 통일**합니다. 이는 프론트엔드에서 Mapbox GL JS를 사용하여 실내외 지도를 연속적으로 렌더링할 때 WGS84를 기본 규격으로 요구하기 때문이며, 데이터 정합성을 일원화하기 위함입니다.

### Consequences

* JPA 영속화 과정에서 공간 geometry 데이터를 매핑하기 위해 `hibernate-spatial` 라이브러리를 활용하거나, Native Query 또는 JdbcTemplate를 사용해 명시적인 공간 쿼리를 실행하여 변환 오차 없이 적재되도록 설계해야 함.

---

## 17. Barrier-free Availability Logic (편의 여부 파싱 규칙)

### Context & Alternatives

`detailWithTour2` API에서 응답받은 텍스트 필드는 구조화되지 않은 자연어 텍스트 형태가 많습니다. (예: "장애인 주차 구역이 있음", "휠체어 대여 불가", "단차 없음", "" 등)
이로부터 해당 시설이 휠체어 전용 시설을 이용할 수 있는지 여부인 `is_available` (Boolean) 값을 정밀하게 도출해야 합니다.

### Decision

* 다음과 같은 **3단계 파싱/추출 규칙**을 개발 및 적용합니다.
  1. 빈 문자열(Null/Empty)이거나 공백만 존재하면 -> `false`
  2. 텍스트 내에 부정적인 표현("없음", "불가", "미설치", "어려움")이 직접 명시되어 있으면 -> `false`
  3. 그 외 구체적인 묘사나 설명("있음", "설치", "대여 가능" 등)이 있거나 유의미한 내용이 기재되어 있으면 -> `true`

### Consequences

* 텍스트에 기반한 휴리스틱 판정 로직이 적용되므로 예외 케이스(예: "휠체어 동반자가 없으면 불가")에 대한 정밀한 유닛 테스트 검증이 중요함.

---

## 18. Region Scope (수집 대상 지역 범위)

### Context & Alternatives

수집 지역을 서울이나 특정 시범 지역으로 한정할 것인지, 아니면 전국 단위 데이터를 수집할 것인지에 대한 설계 범위 설정입니다.

### Decision

* **전국 단위 수집**을 기본 목표로 합니다. API 호출 시 특정 `areaCode` 필터를 두지 않고, Pagination(전체 페이지 순차 조회)을 통하여 전국의 무장애 정보를 빠짐없이 긁어모으도록 구성합니다.

### Consequences

* 초기 데이터 양이 상당하므로 배치 실행 시간(Execution Timeout)에 대비해 페이징 사이즈(`pageSize` 및 `chunkSize`)를 정교하게 튜닝해야 함.

---

## 19. Package Directory Structure (패키지 구조)

### Context & Alternatives

배치 모듈이 추가됨에 따라 백엔드 애플리케이션의 패키지 분리 규칙을 수립해야 합니다.

### Decision

* 기존 백엔드 패키지(`kr.bi.go_to`) 하위에 **`batch` 또는 `job` 이라는 독립 패키지를 분리**하여 적재 관련 코드들을 모아둡니다.
  * 예: `kr.bi.go_to.batch.etl.job` (배치 잡 설정)
  * 예: `kr.bi.go_to.batch.etl.reader` / `processor` / `writer`

### Consequences

* 핵심 웹 비즈니스 로직과 배치 인프라 로직이 논리적으로 격리되어, 모듈식 확장이 용이해지고 전체 프로젝트 코드 가독성이 유지됨.

---

## 20. Automated & Integration Testing Strategy (테스트 검증 방안)

### Context & Alternatives

복잡한 ETL 로직이 올바르게 굴러가는지 지속적으로 검증할 테스트 전략 설정입니다.

### Decision

* **유닛 테스트 + 통합 테스트(Testcontainers) 병행** 전략을 적용합니다.
  * **ItemProcessor (Transform)**: 텍스트 파싱 로직 및 DTO 변환 규칙은 목(Mock) 객체 기반의 순수 Java 유닛 테스트로 신속하게 검증.
  * **ItemWriter (Load)**: 공간 DB(PostGIS) 적재 및 Upsert 쿼리 동작은 Docker 기반의 **PostgreSQL Testcontainers**를 띄워 실제 DB 레벨에서의 연동 정합성을 통합 테스트로 검증.

### Consequences

* 실제 배포 환경과 동일한 PostGIS DB 상태에서 마이그레이션 성공 여부를 사전에 자동 검증할 수 있어 런타임 데이터 에러를 방지할 수 있음.
