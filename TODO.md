# 액션 아이템 — 공간 데이터 및 지도 서빙

> 담당 요구사항: REQ-PM-02 / REQ-PM-03 / REQ-PM-04
> 기술 스택: Java 21 + Spring Boot, PostgreSQL + PostGIS, Redis, Mapbox

---

## ⚠️ 현재 블로커

> **실내 도면 데이터 확보 방법이 결정되기 전까지 REQ-PM-02/03의 실제 데이터 적재는 불가능합니다.**
> 아래 블로커를 먼저 기획/PM팀과 협의하세요.

- 한국관광공사 API(`detailCommon2` 등)는 장소 기본 정보만 제공하며, **실내 층별 폴리곤 도면은 별도 확보 필요**
- 확보 방법 후보 (선택 필요):
  - [ ] 장소 운영 기관에 직접 도면 요청 (CAD → GeoJSON 변환)
  - [ ] 실내지도 전문 업체 활용 (네이버 실내지도 파트너 등)
  - [x] MVP 한정 수동 GeoJSON 직접 제작 — 기획팀 협의 전까지 임시로 채택, 개발 진행을 위해 샘플 데이터로 병행
- MVP 시범 장소 수 기획팀과 확정 필요

---

## REQ-PM-04 | 지도 데이터 확보

### 목표
한국관광공사 API를 통해 MVP 시범 장소의 기본 정보를 확보하고, 실내 도면 확보 경로를 결정한다.

| # | 액션 아이템 | 비고 |
|---|------------|------|
| 1 | `areaBasedSyncList2` / `detailCommon2` 호출하여 MVP 시범 장소 `contentid` 목록 수집 | 국립경주박물관 등 |
| 2 | 실내 도면 확보 방법 결정 (기획팀 협의) | **블로커 — 최우선 해결** |
| 3 | MVP 시범 장소 수 확정 | 도면 확보 방법에 따라 범위 결정 |
| 4 | 확보된 도면이 GeoJSON 폴리곤 형식이 아닐 경우 변환 계획 수립 | CAD/SVG → GeoJSON 변환 도구 검토 |

---

## REQ-PM-03 | 실내 시설물 연동 (노드 좌표계)

### 목표
`FACILITY_NODE` 테이블에 엘리베이터·화장실 등 시설 노드를 실내 좌표(`geojson_point`)와 체크포인트 여부(`is_checkpoint`)와 함께 저장한다.

### 액션 아이템

**1. PostGIS + hibernate-spatial 셋업**

- [x] `build.gradle`에 의존성 추가
  ```gradle
  implementation 'org.hibernate.orm:hibernate-spatial'
  ```
- [x] `application.yml` dialect 설정 — Hibernate 7 자동 감지, 불필요
- [x] DB에서 PostGIS 확장 활성화 확인
  ```sql
  CREATE EXTENSION IF NOT EXISTS postgis;
  ```

**2. `FLOOR_MAP` / `FACILITY_NODE` DDL 및 엔티티 구현**

- [x] ERD 기준으로 DDL 작성 및 실행
- [x] `FLOOR_MAP` 엔티티 구현 (`geojson_data`는 `FloorGeoJson` 타입으로 매핑)
- [x] `FACILITY_NODE` 엔티티 구현
- [x] `Place`/`Member` 엔티티 연동 (`FloorMap.place` → `@ManyToOne Place`, `createdBy` → `@ManyToOne Member`)
  - 다른 티켓(`datamodel 기본 작성`)에서 `model.map.FloorMap`/`FacilityNode`로 이미 구현되어 main에 병합됨
  - 이 브랜치가 독자적으로 만들었던 `model.floorMap`/`model.facilityNode` 패키지와 `V2__create_floor_map_and_facility_node.sql`(존재하지 않는 `place`/`member` 단수 테이블 참조, Flyway `V2` 버전 충돌)은 중복이라 삭제하고 `model.map.*`으로 통일함

**2-1. 관리자용 도면 등록/수정 API 구현**

- [x] 엔드포인트: `PUT /admin/places/{placeId}/floors/{floor}` — GeoJSON `FeatureCollection`을 바디로 받아 업서트
- [x] `(place_id, floor_level)` 유니크 제약 추가 (`V10__add_floor_maps_unique_constraint.sql`) — 업서트 조회가 `Optional` 단일 결과를 기대하므로 중복 row 방지
- [x] `createdBy`는 최초 생성 시에만 설정, 이후 업데이트에서는 유지
- [x] 자동화 테스트 작성 완료 (`AdminFloorMapControllerIntegrationTest`, `FloorMapRepositoryTest`)

**3. `target_feature_id` 연결 로직 구현**

- [x] GeoJSON `properties.node_id`와 `FACILITY_NODE.target_feature_id` 매핑 검증 로직 구현
- [x] 노드 등록 시 `targetFeatureId`가 해당 층 GeoJSON 내에 실제로 존재하는지 확인하는 유효성 검사 추가 (`FacilityNodeService.validateTargetFeatureId`, 없으면 400 `TARGET_FEATURE_NOT_FOUND`)
  - `targetFeatureId`가 비어있으면 검증 스킵 (선택 필드)
  - 실제 RDS에 대고 오타/정상/미입력 3가지 케이스 수동 검증 완료
  - 자동화 테스트 작성 완료 (`AdminFacilityNodeControllerIntegrationTest`)

**4. 관리자용 노드 등록 API 구현**

- [x] 엔드포인트: `POST /admin/places/{placeId}/floors/{floor}/nodes`
- [x] 요청 바디 필드: `nodeType`, `name`, `lat`, `lng`, `isCheckpoint`, `snapRadius`, `targetFeatureId`
- [x] 응답: 생성된 노드 ID 및 전체 필드 반환
- [x] `createdBy`를 인증된 사용자(`AuthenticatedMember`)로 채움 (기존 `null` 하드코딩 제거)
- [ ] ADMIN 역할 권한 체크 — 현재는 인증만 요구, role 검증 없음 (별도 티켓으로 분리)

**5. 시설 상태 제보 API 구현 (Notion 12.3 "상태 제보하기"/"위치가 달라요")**

- [ ] `Report` 엔티티(`node_id`, `reporter_id`, `issue_type`, `description`)는 이미 있으나 컨트롤러/서비스 없음
- [ ] `POST /api/v1/places/{placeId}/floors/{floor}/nodes/{nodeId}/reports` — 상태 제보 생성
- [ ] `GET /api/v1/places/{placeId}/floors/{floor}/nodes/{nodeId}` 응답에 마지막 제보 시각 포함 (바텀시트용)
- [ ] "위치가 달라요" 제보 유형 정의 (`issue_type=LOCATION_WRONG` 등) 및 노드 좌표 보정 반영 플로우 결정

**6. 시설 노드 위치 설명 텍스트 필드 추가 (Notion 12.3 바텀시트 위치 안내)**

- [x] `facility_nodes.location_description` 컬럼 추가 (`V11__add_facility_node_location_description.sql`)
- [x] `FacilityNode` 엔티티/`CreateFacilityNodeRequest`/`FacilityNodeResponse`에 필드 반영
- [x] 관리자 등록 API(`POST /admin/places/{placeId}/floors/{floor}/nodes`)에서 값 저장하도록 연결
- [x] 조회 API(`GET /api/v1/places/{placeId}/floors/{floor}/nodes`) 응답에 포함되는지 확인 완료
- [x] 프론트 노드 상세 바텀시트에 위치 설명 렌더링 (`goto-fe` `IndoorMapScreen.tsx`) — 값 없는 노드는 해당 줄 생략

---

## REQ-PM-02 | 벡터 지도 서빙

### 목표
층별 도면(`FLOOR_MAP.geojson_data`)을 GeoJSON으로 서빙하고, Redis 캐싱을 적용하여 성능을 확보한다.

### 액션 아이템

**1. Redis 셋업**

- [ ] `build.gradle`에 의존성 추가
  ```gradle
  implementation 'org.springframework.boot:spring-boot-starter-data-redis'
  ```
- [ ] `application.yml` Redis 연결 설정
  ```yaml
  spring.data.redis:
    host: localhost
    port: 6379
  ```

**2. 층별 GeoJSON 서빙 API 구현**

- [x] 엔드포인트: `GET /places/{placeId}/floors/{floor}/indoor-map` (계획한 쿼리파라미터 형태 대신, 관리자용 API와 대칭되는 path variable 형태로 구현)
- [x] DB에서 `FLOOR_MAP.geojson_data` 조회 후 그대로 반환
- [x] `Content-Type: application/geo+json` 설정
- [x] 자동화 테스트 작성 (`IndoorMapControllerIntegrationTest`)

**3. Redis 캐싱 적용**

- [ ] `@Cacheable` 적용
  ```java
  @Cacheable(value = "indoor-map", key = "#placeId + ':' + #floor")
  public String getFloorGeoJson(Long placeId, int floor) { ... }
  ```
- [ ] TTL 설정: 도면은 자주 변경되지 않으므로 **24시간 이상** 권장
- [ ] 캐시 키 규칙: `indoor-map:{placeId}:{floor}`

**4. 캐시 무효화 처리**

- [ ] 도면 업데이트 시 해당 캐시 삭제
  ```java
  @CacheEvict(value = "indoor-map", key = "#placeId + ':' + #floor")
  public void updateFloorMap(Long placeId, int floor, String geojson) { ... }
  ```

**5. `FACILITY_NODE` 포함 여부 프론트엔드와 협의**

- [x] 별도 API(`GET /places/{placeId}/floors/{floor}/nodes`)로 분리하기로 결정 및 구현 — 관리자용 쓰기 API(도면/노드 각각 별도 엔드포인트) 구조와 대칭, 캐싱 단위 분리도 쉬움
- [x] 자동화 테스트 작성 (`PlaceFacilityNodeControllerIntegrationTest`, `FacilityNodeRepositoryTest`)

**6. 장소별 층 목록 조회 API 구현**

- [x] 엔드포인트: `GET /api/v1/places/{placeId}/floors` — `FLOOR_MAP.floor_level` 목록을 오름차순으로 반환 (`PlaceFloorController`/`PlaceFloorApiSpec`/`FloorMapService.listFloorLevels`)
- [x] 자동화 테스트 작성 (`PlaceFloorControllerIntegrationTest`, `FloorMapRepositoryTest` 추가 케이스)
- [x] 프론트 층 선택 UI가 현재 `DEMO_FLOORS` 하드코딩([1, 2, -1])으로 임시 대응 중 — 이 API로 교체 필요 → `IndoorMapScreen`이 이 API로 층 목록을 직접 조회하도록 교체 완료
- Redis 캐싱(1~4번 항목)은 이번 스코프에서 의도적으로 제외 — 프론트엔드 연동 우선, 캐싱은 후속 작업

---

## REQ-PM-02-FE | 프론트엔드 실내지도 렌더링 (네이버 지도)

### 배경
Mapbox → 네이버 지도(`@mj-studio/react-native-naver-map`) 전면교체 스파이크 완료. 백엔드 API(`indoor-map`, `nodes`)는 그대로 두고 프론트엔드 렌더링 레이어만 교체, `IndoorMapScreen.tsx`에서 도면 폴리곤 + 체크포인트 노드 오버레이 렌더링까지 에뮬레이터로 검증함.

### 액션 아이템

- [x] 층 선택 UI 컴포넌트 추가 (1F/B1/B2 등 — POC `maps-mock.joonamin.dev` 참고) — `IndoorMapScreen`이 층을 내부 상태로 관리하도록 변경, 우상단 세로 버튼 리스트로 구현. 이후 `GET /api/v1/places/{placeId}/floors` API로 실제 층 목록을 조회하도록 교체 완료 (하드코딩 제거)
- [x] 층 전환 시 지도 오버레이(폴리곤)가 깨지는 버그 수정 — `NaverMapView`를 계속 마운트 유지하고, 이전 층 도면 데이터를 다음 층 데이터가 준비될 때까지 유지하도록 구조 변경
- [ ] 실제 여러 방/복도 형태의 폴리곤 도면 데이터 제작 — **1F만 완료** (101~106호 + 중앙복도), B1/2F는 아직 층 선택 UI 테스트용 사각형 1개짜리 샘플 그대로
- [ ] 범례(운임/비운임 구역, 개찰구, 체크포인트 등) UI 추가 — 미착수

---

## REQ-PM-05 | 홈 지도 탐색 API

> Notion 화면 정의서 8장(홈 지도) 대응. 프론트 작업은 `goto-fe/TODO.md` 참고.

### 목표

이동조건·회피구간·장소유형 필터와 "내 주변 접근성 정보" 요약을 제공하는 탐색 API를 완성한다.

### 현재 상태

`GET /api/places/search`(`PlaceController`)는 `lat`/`lng`/`k`/`category`만 지원. 스펙의 이동조건/회피구간 필터, 저장 장소, 실내 지도 가능 여부 플래그는 미반영.

### 액션 아이템

**1. 탐색 필터 확장**

- [ ] `PlaceSearchRequest`에 이동조건(`mobilityType`: 휠체어/유모차/느린보행) 필드 추가
- [ ] 회피 구간 필터(`avoid`: 높은턱/보도파손/급경사/공사) 추가 — REQ-PM-06 장애물 리포트 데이터와 조인 필요
- [ ] 장소 유형 필터(관광지/식당/숙소/화장실 등)와 기존 `category` 필드 관계 정리
- [ ] 적용된 필터를 `PlaceSearchResponse.filters`에 echo (프론트 필터 칩 상태 복원용)

**2. 지도 레이어 지원**

- [ ] "저장 장소" 즐겨찾기 기능 신규 구현 (`SavedPlace` 엔티티, `POST/DELETE /api/places/{id}/save`, `GET /api/me/saved-places`)
- [ ] "실내 지도 진입 가능 장소" 레이어 — `FLOOR_MAP` 존재 여부 플래그 (`PlaceSearchItemResponse.hasIndoorMap`) 추가
- [ ] "장애물 리포트" 레이어는 REQ-PM-06 클러스터 API 그대로 사용

**3. "내 주변 접근성 정보" 요약 API**

- [ ] `GET /api/places/nearby-summary`(가칭) — 최근 확인된 장소 수 / 주의 제보 수 / 정보 업데이트 필요 장소 수
- [ ] "최근" 판단 기준 시간 윈도우 정책 확정 (예시 목업 기준 48시간 = "2일 이내")

---

## REQ-PM-06 | 장애물 리포트 및 클러스터링 API

> Notion 화면 정의서 9장(장애물 리포트 지도 클러스터링) 대응. 프론트 작업은 `goto-fe/TODO.md` 참고.

### 목표

위치 기반 장애물 제보 CRUD와 줌 레벨별(지역/블록/개별 핀) 클러스터링 API를 구현한다.

### 현재 상태

기존 `Report` 엔티티는 `node_id`(실내 시설 노드) 전용이라 실외 장애물 제보(위/경도 기반 보도 파손·공사·높은 턱 등)에는 그대로 쓸 수 없음 — 별도 도메인 필요.

### 액션 아이템

**1. 장애물 리포트 도메인 신설**

- [ ] `ObstacleReport` 엔티티 설계 (위/경도, 유형, 심각도(통행어려움/주의/정보), 사진, 작성자, 상태(확인됨/해결됨/오래됨), 영향받는 사용자 유형)
- [ ] `POST /api/obstacle-reports` — 제보 생성(사진 첨부 포함)
- [ ] `POST /api/obstacle-reports/{id}/status` — "아직 있어요"/"해결됐어요" 반영

**2. 줌 레벨별 클러스터링 API**

- [ ] `GET /api/obstacle-reports/clusters?bbox=&zoom=` — 지역/블록/개별 핀 3단계 표현
- [ ] 클러스터 요약 필드: 리포트 수, 최고 심각도, 주요 유형 top3, 최근 제보 시각, 확인/해결/오래됨 카운트
- [ ] 클러스터링 알고리즘·그리드 크기 정책 결정 (PostGIS `ST_ClusterDBSCAN` 등 후보 검토)

**3. 경로 연동 (후속, 낮은 우선순위)**

- [ ] 경로 위 주의 구간 배지용 API — 경로 안내 기능 자체가 아직 없어 별도 REQ로 분리 필요

---

## 전체 작업 순서

```
① 실내 도면 확보 방법 확정 (기획팀 협의)  ← 블로커, 최우선
        ↓
② PostGIS + h도며
④ 관리자용 노드 등록 API 구현
        ↓
⑤ GeoJSON 서빙 API + Redis 캐싱 구현
        ↓
⑥ 프론트엔드와 Mapbox 연동 테스트
        ↓
⑦ 실제 도면 데이터 적재 및 검증 (도면 확보 후)
```

> ②~⑥은 블로커와 병렬 진행 가능합니다. 도면이 없어도 API 껍데기와 캐싱 로직은 먼저 구현해둘 수 있어요.
