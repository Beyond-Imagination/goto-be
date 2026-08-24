---
author: 정은영 (jey0623@gmail.com)
date: 2026-08-09
status: Accepted
---

# ADR-0005: "내 주변 접근성 정보" 요약 API — 신규 확인 도메인 대신 기존 장애물 리포트 4분류 재사용

## Context

`REQ-PM-05` TODO 원문은 `GET /api/places/nearby-summary`(가칭)가 "최근 확인된 장소 수 / 주의 제보 수 / 정보 업데이트 필요 장소 수"를 반환해야 한다고 적고 있습니다. 그런데 "확인된 장소"라는 개념이 코드베이스에 없습니다 — `Place`/`PlaceBfInfo`엔 확인 시각 필드가 없고, `ObstacleReportConfirmation`은 장애물 리포트에 대한 확인이지 장소에 대한 확인이 아닙니다. 이를 문구 그대로 구현하려면 `PlaceConfirmation` 같은 신규 도메인(엔티티 + "여기 정보 맞아요" 액션 API)을 새로 설계해야 해서, 요약 API 하나 붙이는 작업의 스코프가 크게 늘어납니다.

한편 `REQ-PM-06` 클러스터 API의 먼 줌 화면은 이미 우회권장(`IMPASSABLE`)/주의(`CAUTION`)/안전(`INFO`)/확인필요(`STALE`) 4분류를 **장애물 리포트 건수 집계**로 정의해뒀습니다(`CONTEXT.md` "먼 줌/중간 줌/가까운 줌 카테고리" 항목).

## Decision

"내 주변 접근성 정보" 요약 API는 "확인된 장소" 개념을 폐기하고, 기존 4분류를 그대로 재사용해 `ObstacleReport` 데이터 기반으로 계산합니다.

`GET /api/v1/places/nearby-summary?lat=&lng=` → `NearbyAccessibilitySummaryResponse(detourRecommendedCount, cautionCount, safeCount, needsConfirmationCount)`

세부 정책:
- 4개 카운트는 `severity` 축(우회권장/주의/안전 중 하나)과 `STALE` 축(확인필요)이 서로 독립이라, 하나의 리포트가 두 카운트에 동시에 집계될 수 있습니다(클러스터 API의 상태별 하위 집계와 동일한 원칙 — 합계가 전체 리포트 수와 다를 수 있음을 인지하고 설계).
- `RESOLVED` 상태는 클러스터 API와 달리 이 요약에서는 **전부 제외**합니다. 이 위젯은 "지금 조심해야 할 상황"을 보여주는 용도라, 이미 해결된 문제까지 포함하면 실제보다 위험하게 보일 수 있기 때문입니다.
- 반경은 서버 고정 상수를 사용하고 클라이언트가 지정하지 않습니다 — 반경을 사용자가 조절하는 UI가 기획에 없는 상태에서 파라미터로 미리 열어둘 이유가 없습니다.

## Consequences

새 엔티티/마이그레이션 없이 기존 `ObstacleReportRepository` 조회 패턴을 재사용해 바로 구현할 수 있습니다.

다만 "확인된 장소"라는 원래 기획 의도(사용자가 능동적으로 장소 정보를 확인했다는 신호)는 이번에 구현되지 않습니다. 이 개념이 실제로 필요해지면 별도 `PlaceConfirmation` 도메인을 새로 설계해야 하며, 이 ADR은 그 가능성을 배제하는 것이 아니라 지금 시점에는 채택하지 않는다는 결정입니다.
