---
author: 정은영 (jey0623@gmail.com)
date: 2026-08-30
status: Accepted
---

# ADR-0006: 홈 지도 클러스터 API — 이동조건 다중 선택 전환, 회피구간 필터·최근접 장소 라벨 추가

## Context

`CONTEXT.md`의 "PlaceSearchRequest 필터" 항목은 `ObstacleReportClusterRequest.mobilityType`을 "홈 지도 상단 토글용" 단일값으로, `PlaceSearchRequest.mobilityTypes`(다중)와 의도적으로 다른 형태로 설계했다고 기록하고 있었다.

이번에 goto-fe에서 Figma 최신 홈 지도 화면(먼 줌/중간 줌/가까운 줌)을 기준으로 그릴링 세션을 진행하며 이 설계를 다시 검토했다. 실제 화면은 휠체어/유모차 등 이동조건 칩을 **동시에 여러 개** 선택할 수 있는 UI였고, 사용자(프로덕트 오너)도 다중 선택이 맞다고 명시적으로 확인했다. 단일값 토글이라는 기존 전제가 최신 디자인과 맞지 않았다.

같은 화면 검토 과정에서 두 가지가 더 필요하다는 게 드러났다:
- "높은턱 제외"처럼 특정 장애물 유형을 클러스터 결과에서 빼는 필터 — `PlaceSearchRequest.avoid`와 같은 개념이지만 클러스터 API엔 없었다.
- 중간 줌 화면의 "주변 접근성 이슈" 카드는 클러스터를 좌표가 아니라 "OO 인근" 장소명으로 보여줘야 하는데, 이 라벨을 만들 방법이 없었다.

## Decision

**1. `mobilityType`(단일) → `mobilityTypes`(다중)로 전환.** `PlaceSearchRequest.mobilityTypes`와 동일하게 `Set<MobilityType>`으로 바꾼다. 위 CONTEXT.md의 "의도적으로 다름" 서술은 이 ADR로 대체된다.

**2. `avoid: Set<ObstacleIssueType>` 필드를 신규 추가.** `PlaceSearchRequest.avoid`와 동일한 이름·타입을 재사용해, 특정 장애물 유형을 클러스터 조회에서 제외할 수 있게 한다.

**3. `ObstacleReportClusterResponse`에 `nearbyPlaceLabel: String`(nullable)을 추가.** 서비스 계층에서 클러스터 중심 좌표 기준으로 (1) `PlaceRepository.findNearbyActivePlaces`(500m, 기존 메서드 재사용)로 최근접 활성 장소를 먼저 찾고, (2) 못 찾으면 신규 `NaverReverseGeocodingClient`(네이버 클라우드 플랫폼 Reverse Geocoding API)로 행정동을 폴백 조회한다. 둘 다 실패하면 `null` — 이 경우 프론트는 "주변 접근성 이슈" 카드 리스트에서만 해당 클러스터를 제외하고, 지도 마커 자체는 좌표 기준으로 계속 표시한다.

벤더는 네이버를 선택했다 — 프론트가 이미 Naver Map SDK(`@mj-studio/react-native-naver-map`)를 쓰고 있어 같은 콘솔/키 체계를 재사용할 수 있다. 외부 호출 실패는 예외를 던지지 않고 `Optional.empty()`로 흡수한다(`docs/adr/0003`의 Redis 캐시 장애 흡수 정책과 같은 원칙) — 라벨링 실패가 클러스터 API 전체를 깨뜨려선 안 된다.

`nearbyPlaceLabel` 계산은 외부 API 호출 비용이 있으므로 중간 줌 구간(`FAR_ZOOM_UPPER_BOUND` ≤ zoom < `CLOSE_ZOOM_THRESHOLD`)에서만 수행한다. 먼 줌은 최대 5개 클러스터만 반환하니 비용 자체는 작지만, 먼 줌 카드는 좌표 라벨이 필요 없는 별개 위젯(`nearby-summary` API)이라 애초에 이 필드를 쓰지 않는다.

## Consequences

- 클러스터/장소 검색 두 API의 필터 파라미터 형태가 다시 대칭을 이루게 됐다(`mobilityTypes`/`avoid` 이름과 타입 동일).
- 새 마이그레이션은 필요 없다 — 세 변경 모두 요청/응답 DTO와 서비스 계층 로직만 바뀐다.
- 네이버 리버스 지오코딩 API 키(`naver-reverse-geocoding.client-id`/`client-secret`)가 Parameter Store에 새로 필요하다. 키가 없으면 `NaverReverseGeocodingClient`가 호출 자체를 건너뛰므로, 키 프로비저닝 전까지는 최근접 장소 매칭에만 의존해 라벨이 더 자주 비게 된다(기능은 깨지지 않는다).
- `ObstacleReportClusterRequest`를 이미 호출하는 클라이언트가 있다면 `mobilityType`(단일) 파라미터가 더 이상 유효하지 않다 — 이번 스코프에서는 goto-fe가 유일한 클라이언트라 하위 호환을 신경 쓰지 않았다.
