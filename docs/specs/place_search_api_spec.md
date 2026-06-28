# Technical Specification: Place Search API

**Document Status:** Final  
**Author:** InGyu Moon (ansdlsrb1121@naver.com)  
**Date:** 2026-06-28  

## 1. 개요

### 1.1 목적

본 문서는 앱 홈 검색 화면에서 사용하는 위치 기반 장소 탐색 API의 요청·응답 계약과 현재 Mock 기반 구현 구조를 정의합니다.

### 1.2 범위

- **포함:** 현재 위치 기반 거리 계산, 카테고리 필터링, 결과 개수 제한, 필터 정보 반환, 요청값 검증, OpenAPI 문서화
- **제외:** 실제 `PLACE`, `PLACE_BF_INFO` Repository 연동, PostGIS 공간 쿼리, 운영 데이터 조회

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

1. `PlaceService.findAll()`로 장소 원본 목록을 조회합니다.
2. 전체 원본 목록에서 중복을 제거한 카테고리를 오름차순으로 구성합니다.
3. `category`가 지정되면 문자열이 정확히 일치하는 장소만 남깁니다.
4. 사용자 좌표와 각 장소 좌표 사이의 거리를 Haversine 공식으로 계산합니다.
5. `distanceMeters`를 기준으로 오름차순 정렬합니다.
6. 정렬된 결과에서 최대 `k`개만 반환합니다.

카테고리 필터 목록은 현재 선택된 카테고리와 무관하게 전체 장소 데이터에서 생성합니다.

### 3.2 거리 계산

지구 반지름은 `6,371,000m`를 사용합니다.

```text
a = sin²(Δlat / 2)
  + cos(lat1) × cos(lat2) × sin²(Δlng / 2)

distance = 2 × 6,371,000 × asin(√a)
```

응답의 `distanceMeters`는 소수점 첫째 자리까지 반올림합니다.

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
└── model/
    ├── PlaceData
    └── BfDetailsData
```

각 계층의 책임:

- `PlaceController`: HTTP 요청 바인딩과 응답 반환
- `SearchPlacesUseCase`: 필터링, 거리 계산, 정렬, 개수 제한 및 응답 조립
- `PlaceService`: 장소 데이터 공급 계약
- `MockPlaceService`: 운영 데이터 소스 연결 전 임시 장소 데이터 제공
- `service.place.model`: Service와 UseCase 사이의 내부 데이터 계약
- `controller.place.request/response`: 외부 HTTP API 계약

`SearchPlacesUseCase`는 `MockPlaceService`가 아닌 `PlaceService` 인터페이스에만 의존합니다. 따라서 실제 DB 구현체가 추가되어도 검색 처리 로직은 유지할 수 있습니다.

---

## 5. Mock 데이터와 향후 전환

현재 `MockPlaceService`는 서울 지역 장소 6건을 불변 리스트로 제공합니다.

- 카테고리: 관광지, 공공기관, 숙박
- PLACE 대응 정보: ID, 외부 ID, 출처, 카테고리, 이름, 주소, 좌표, 썸네일
- PLACE_BF_INFO 대응 정보: 엘리베이터, 장애인 화장실, 경사로, 마지막 동기화 시각

실제 데이터 연동 시 `PlaceService` 구현체를 추가하고 `PLACE`, `PLACE_BF_INFO` 조회 결과를 `PlaceData`로 변환합니다. 운영 구현에서는 ADR-0000에 정의된 PostGIS `location_point`와 공간 인덱스 활용을 우선 검토합니다.

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
- `k` 기본값 10 적용

### 7.2 `PlaceControllerTest`

- query parameter 바인딩
- category 필터 응답
- 위도·경도 범위 검증
- `k` 최대값 검증
- 필수 좌표 누락 시 `400 Bad Request`

### 7.3 `SwaggerTagTest`

- Swagger 태그가 `A. Auth`, `C. Place` 순서로 구성되는지 검증
