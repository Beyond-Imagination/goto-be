---
author: 정은영 (jey0623@gmail.com)
date: 2026-08-09
status: Accepted
---

# ADR-0004: REQ-PM-05 탐색 필터 확장 스코프 — API 모양만 구현, DB 필터링은 `DbPlaceService`로 이관

## Context

`REQ-PM-05 | 홈 지도 탐색 API`는 `PlaceSearchRequest`에 이동조건(`mobilityTypes`)/회피구간(`avoid`)/장소유형(`categoryPrefixes`) 필터를 추가하고, 적용된 필터를 응답에 echo하도록 요구합니다.

그런데 `PlaceService`의 유일한 구현체는 `MockPlaceService`(하드코딩된 장소 6개)이고, `DbPlaceService`는 아직 존재하지 않습니다. Tour API 동기화 배치는 실제로 `places`/`place_bf_info`를 채우고 있지만, 검색 read path(`SearchPlacesUseCase`)는 이 데이터를 전혀 조회하지 않습니다. `DbPlaceService` 구현은 이미 다른 두 항목에서 "나중에 한 번에 풀 문제"로 미뤄져 있습니다 — `hasIndoorMap`의 실제 DB 연동, `SavedPlaceResponse.bfDetails` 매핑. 여기에 세 번째로 필터링 로직(카테고리 prefix 매칭, `PlaceBfInfo` 기반 이동조건 적합성 판정, `ObstacleReport`와의 위치 조인)까지 얹으면, 원래 "필터 API 붙이기"였던 이번 작업이 사실상 "`DbPlaceService` 전체 구현" 작업으로 커집니다.

## Decision

이번 스코프에서는 `PlaceSearchRequest`에 `mobilityTypes`(`Set<MobilityType>`), `avoid`(`Set<IssueType>`), `categoryPrefixes`(`Set<String>`) 필드를 추가하고 `PlaceSearchResponse.appliedFilters`로 echo하지만, **`MockPlaceService`에서는 세 필터 모두 no-op으로 처리**(항상 전체 목록 반환)합니다. 기존 `category`(정확 일치) 필드는 제거하고 `categoryPrefixes`로 통합합니다. 요청 필드명과 응답 echo 필드명(`AppliedFiltersResponse`)은 셋 다 동일하게 맞춥니다 — 프론트가 필터 칩 상태를 대칭적으로 복원할 수 있어야 하기 때문입니다.

실제 DB 기반 필터링은 `DbPlaceService` 구현 시점에 세 필터를 한 번에 붙입니다.

## Consequences

필터 파라미터를 보내도 검색 결과는 바뀌지 않는 상태로 머지됩니다 — API 문서(Swagger)에 이 사실을 명시해 프론트 개발/QA 단계에서 혼동이 없도록 합니다.

`DbPlaceService`가 구현되는 시점에 `hasIndoorMap` 연동, `bfDetails` 매핑, 이 필터 세 개가 한 PR에서 함께 다뤄질 가능성이 높습니다 — 그 PR을 계획할 때 이 ADR을 참고합니다.
