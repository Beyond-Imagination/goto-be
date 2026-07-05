# Technical Specification: Place Search API

**Document Status:** Final  
**Author:** InGyu Moon (ansdlsrb1121@naver.com)  
**Date:** 2026-06-29  

## 1. 개요

### 1.1 목적

본 문서는 앱 홈 검색 화면에서 사용하는 위치 기반 장소 탐색 API의 요청·응답 계약과 운영 DB 검색 구조를 정의합니다.

### 1.2 범위

- **포함:** 현재 위치 기반 거리 검색, 카테고리 필터링, 결과 개수 제한, 필터 정보 반환, 요청값 검증, PostGIS 검색 책임, Mock에서 운영 DB로의 전환 구조, OpenAPI 문서화
- **제외:** Repository와 SQL의 구체적인 프레임워크 선택 및 배포 환경별 DB 설정

---

## 2. API 계약

### 2.1 Endpoint

```http
GET /api/places/search
```

기존 Spring Security 정책에 따라 인증이 필요하며, 유효한 JWT access token을 전달해야 합니다.

```http
Authorization: Bearer {accessToken}
```

### 2.2 Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 제약 | 설명 |
| :--- | :--- | :---: | :---: | :--- | :--- |
| `lat` | Double | O | - | `-90.0` 이상 `90.0` 이하 | 사용자 현재 위도 |
| `lng` | Double | O | - | `-180.0` 이상 `180.0` 이하 | 사용자 현재 경도 |
| `k` | Integer | X | `10` | `1` 이상 `50` 이하 | 반환할 최대 장소 수 |
| `category` | String | X | - | 공백 문자열은 필터 미적용 | 조회할 장소 카테고리 |

요청 예시:

```http
GET /api/places/search?lat=37.5665&lng=126.9780&k=10&category=관광지
```

### 2.3 Response

```json
{
  "places": [
    {
      "placeId": 3,
      "name": "경복궁",
      "category": "관광지",
      "address": "서울 종로구 사직로 161",
      "thumbnailUrl": "https://example.com/palace.jpg",
      "latitude": 37.579617,
      "longitude": 126.977041,
      "distanceMeters": 1461.0,
      "bfDetails": {
        "hasElevator": false,
        "hasAccessibleToilet": true,
        "hasRamp": true
      }
    }
  ],
  "filters": {
    "categories": [
      "공공기관",
      "관광지",
      "숙박"
    ]
  }
}
```

응답 객체 구성:

| 타입 | 역할 |
| :--- | :--- |
| `PlaceSearchResponse` | `places`, `filters`를 포함하는 최상위 응답 |
| `PlaceSearchItemResponse` | 검색된 장소 한 건 |
| `BfDetailsResponse` | 장소별 배리어프리 편의시설 정보 |
| `PlaceFilterResponse` | 검색 화면에서 사용할 카테고리 목록 |

---

## 3. 처리 규칙

### 3.1 검색 순서

1. `SearchPlacesUseCase`는 `lat`, `lng`, `k`, `category`를 `PlaceService.searchNearby()`에 전달합니다.
2. 운영 DB 구현체는 PostGIS 공간 인덱스를 이용해 사용자 좌표 주변의 장소를 검색합니다.
3. `category`가 지정되면 DB에서 문자열이 정확히 일치하는 장소만 조회합니다.
4. DB에서 사용자 좌표와 장소 좌표 사이의 거리를 계산하고 `distanceMeters` 오름차순으로 정렬합니다.
5. DB 쿼리에 `LIMIT k`를 적용하여 필요한 장소만 애플리케이션으로 반환합니다.
6. `PlaceService.findDistinctCategories()`로 전체 데이터의 유효한 카테고리 목록을 별도로 조회합니다.
7. `SearchPlacesUseCase`는 검색 결과와 카테고리 목록을 API 응답으로 조립합니다.

운영 검색에서 `PlaceService.findAll()`을 사용해서는 안 됩니다. 전체 장소를 애플리케이션 메모리에 적재한 뒤 거리 계산, 정렬 및 개수 제한을 수행하면 데이터 증가에 따라 DB 네트워크 전송량, 메모리 사용량 및 CPU 부하가 증가하기 때문입니다.

카테고리 필터 목록은 현재 선택된 카테고리와 무관하게 전체 장소 데이터에서 생성하되, 전체 행을 조회하지 않고 DB의 `DISTINCT` 쿼리를 사용합니다.

### 3.2 거리 계산

운영 DB 구현에서는 PostGIS의 `ST_DistanceSphere` 또는 동일한 거리 계산 함수를 사용합니다. `location_point`의 SRID는 `4326`이어야 하며, 공간 인덱스를 이용해 검색 후보를 제한해야 합니다.

Mock 구현에서 거리를 계산할 때는 지구 반지름 `6,371,000m`를 사용하는 Haversine 공식을 적용합니다.

```text
a = sin²(Δlat / 2)
  + cos(lat1) × cos(lat2) × sin²(Δlng / 2)

distance = 2 × 6,371,000 × asin(√a)
```

응답의 `distanceMeters`는 소수점 첫째 자리까지 반올림합니다.

### 3.3 category null 및 공백 처리

- 요청의 `category`가 `null`이거나 공백이면 카테고리 검색 조건을 적용하지 않고 모든 카테고리를 대상으로 검색합니다.
- 저장된 장소의 `category`가 `null`이거나 공백이면 장소 검색 결과에는 포함될 수 있지만, `filters.categories`에서는 제외합니다.
- Mock 구현에서 카테고리 목록을 생성할 때는 `sorted()` 및 `List.copyOf()`에 null 요소가 전달되지 않도록 null과 공백을 정렬 전에 제거합니다.
- Mock 구현에서 카테고리를 비교할 때는 null이 아님이 보장된 요청값에서 `equals()`를 호출하여 저장된 category가 null이어도 NPE가 발생하지 않도록 합니다.
- 운영 DB의 카테고리 목록 조회는 `category IS NOT NULL` 및 `TRIM(category) <> ''` 조건을 적용합니다.

---

## 4. 구현 구조

```text
controller/place/
├── PlaceController
├── request/
│   └── PlaceSearchRequest
└── response/
    ├── PlaceSearchResponse
    ├── PlaceSearchItemResponse
    ├── PlaceFilterResponse
    └── BfDetailsResponse

usecase/
└── SearchPlacesUseCase

service/place/
├── PlaceService
├── mock/
│   └── MockPlaceService
├── db/
│   └── DbPlaceService
└── model/
    ├── PlaceData
    └── BfDetailsData

repository/place/
└── PlaceSearchRepository
```

각 계층의 책임:

- `PlaceController`: HTTP 요청 바인딩과 응답 반환
- `SearchPlacesUseCase`: 요청 조건 전달, Mock 데이터의 null 안전성 보장 및 응답 조립
- `PlaceService`: 위치·카테고리·개수 조건을 받는 장소 검색 및 카테고리 목록 공급 계약
- `MockPlaceService`: 운영 데이터 소스 연결 전 임시 장소 데이터 제공
- `DbPlaceService`: 운영 검색 조건을 Repository에 전달하고 조회 결과를 서비스 모델로 변환
- `PlaceSearchRepository`: PostGIS 거리 검색, 정렬, 개수 제한 및 카테고리 목록 조회
- `service.place.model`: Service와 UseCase 사이의 내부 데이터 계약
- `controller.place.request/response`: 외부 HTTP API 계약

`SearchPlacesUseCase`는 구현체가 아닌 `PlaceService` 인터페이스에만 의존합니다. Mock과 운영 DB 구현체는 동일한 검색 계약을 구현하며 Spring Profile로 하나만 활성화합니다.

목표 인터페이스는 다음과 같습니다.

```java
public interface PlaceService {
    List<PlaceData> searchNearby(double latitude, double longitude, int limit, String category);

    List<String> findDistinctCategories();
}
```

---

## 5. Mock 데이터와 운영 DB 전환

현재 `MockPlaceService`는 서울 지역 장소 6건을 불변 리스트로 제공합니다.

- 카테고리: 관광지, 공공기관, 숙박
- PLACE 대응 정보: ID, 외부 ID, 출처, 카테고리, 이름, 주소, 좌표, 썸네일
- PLACE_BF_INFO 대응 정보: 엘리베이터, 장애인 화장실, 경사로, 마지막 동기화 시각

현재 Mock 구현은 소량의 고정 데이터를 대상으로 메모리에서 필터링, 거리 계산 및 정렬을 수행할 수 있습니다. 단, 이는 운영 검색 방식이 아니며 운영 DB 구현에서는 `findAll()`을 사용하지 않습니다.

실제 데이터 연동 시 `DbPlaceService`와 `PlaceSearchRepository`를 추가하고 `PLACE`, `PLACE_BF_INFO` 조회 결과를 `PlaceData`로 변환합니다. ADR-0000에 정의된 PostGIS `location_point`와 GiST 공간 인덱스를 사용하여 후보 검색, 거리 정렬 및 `LIMIT`을 DB에서 처리합니다.

구현체 활성화 기준:

- Mock 또는 테스트 프로필: `MockPlaceService`
- Local 및 운영 프로필: `DbPlaceService`

카테고리 목록은 다음과 동등한 쿼리로 별도 조회합니다.

```sql
SELECT DISTINCT category
FROM places
WHERE category IS NOT NULL
  AND TRIM(category) <> ''
ORDER BY category;
```

---

## 6. OpenAPI 문서화

- `PlaceApiSpec`에서 API 설명과 HTTP 응답 코드를 관리합니다.
- 요청·응답 record의 `@Schema`로 필드 설명과 예시를 제공합니다.
- Swagger UI에서는 `C. Place` 태그 아래에 표시됩니다.
- `B. User`는 향후 사용자 API 태그를 위해 예약되어 있습니다.

---

## 7. 테스트

### 7.1 `SearchPlacesUseCaseTest`

- 거리 오름차순 정렬
- `k` 제한 적용
- 카테고리 필터 적용
- 전체 카테고리 필터 정보 반환
- 저장된 category가 null 또는 공백일 때 필터 목록에서 제외
- 저장된 category가 null이고 요청 category가 지정된 경우 NPE 없이 검색 결과에서 제외
- 요청 category가 null인 경우 전체 카테고리를 대상으로 검색
- `k` 기본값 10 적용

### 7.2 `DbPlaceService` / `PlaceSearchRepository` 통합 테스트

- PostGIS 거리 오름차순 정렬
- category 조건의 선택적 적용
- DB 쿼리 단계의 `LIMIT k` 적용
- null 및 공백을 제외한 카테고리 중복 제거와 정렬
- 전체 장소를 애플리케이션으로 조회하지 않는지 검증

### 7.3 `PlaceControllerTest`

- query parameter 바인딩
- category 필터 응답
- 위도·경도 범위 검증
- `k` 최대값 검증
- 필수 좌표 누락 시 `400 Bad Request`

### 7.4 `SwaggerTagTest`

- Swagger 태그가 `A. Auth`, `C. Place` 순서로 구성되는지 검증
