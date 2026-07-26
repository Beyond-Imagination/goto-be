---
author: 정은영 (jey0623@gmail.com)
date: 2026-07-25
status: Accepted
---

# ADR-0003: 실내 지도 API Redis 캐싱 도입 범위와 헬스체크 격리

## Context

`REQ-PM-02 | 벡터 지도 서빙`의 목표는 층별 도면(`FLOOR_MAP.geojson_data`)을 GeoJSON으로 서빙하고 Redis 캐싱으로 성능을 확보하는 것입니다. GeoJSON 서빙 API(`IndoorMapController.getIndoorMap`)와 장소별 층 목록 API는 이미 구현되어 있고, Redis 캐싱(TODO.md REQ-PM-02 항목 1, 3, 4)만 남아 있습니다. 이 잔여 스코프가 GOTO-32입니다.

이 저장소는 이미 유사한 선례를 갖고 있습니다. PostgreSQL은 backend 컨테이너가 직접 호스팅하지 않고, 이미 준비된 외부 AWS RDS에 AWS Parameter Store(`/goto/spring.datasource.*`)로 연결 정보만 주입받아 연결합니다(`docs/specs/backend_runtime_contract.md` 7절). Backend 컨테이너 자체는 stateless로 취급되며 named volume이나 host bind mount를 두지 않습니다(같은 문서 12절). Redis 인프라를 어디에 둘지도 같은 질문이지만, 아직 Redis 인프라 자체가 어디에도 프로비저닝되어 있지 않고, `docs/specs/backend_runtime_contract.md`는 "Host/cloud instance provisioning"과 "Docker Compose 파일 작성"을 backend repository의 범위 밖(Out of Scope)으로 명시하고 있어 이 저장소만으로 prod 토폴로지(외부 관리형 Redis vs main module Compose 내부 컨테이너)를 확정할 수 없습니다.

한편 `spring-boot-starter-data-redis`를 추가하면 Spring Boot Actuator가 `RedisHealthIndicator`를 자동 등록합니다. `application.yaml`은 `management.endpoints.web.exposure.include: health`만 설정되어 있고 `show-details: never`이지만, 개별 컴포넌트 상태는 숨겨져도 **집계(aggregate) 상태**에는 여전히 반영됩니다. `docs/specs/backend_runtime_contract.md` 9절에 따르면 main module 배포 워크플로우는 `/actuator/health`가 5초 간격 30회(최대 150초) 안에 `UP`이 되지 않으면 배포 실패로 간주합니다. prod에는 아직 Redis가 없으므로, 이 헬스 인디케이터를 그대로 두면 GOTO-32 머지 직후 다음 prod 배포가 Redis 연결 실패로 인해 깨질 수 있습니다.

---

## 1. Redis 도입 범위: 로컬 개발 전용, prod 인프라는 별도 결정

### Decision

GOTO-32에서는 Redis를 **로컬 개발 환경(`docker-compose-test.yml`)에만** 추가하고, `@Cacheable`/`@CacheEvict` 기반 캐싱 로직을 완성해 로컬에서 검증합니다. prod Redis 인프라(관리형 서비스 vs main module Compose 컨테이너)는 이 ADR의 범위에 포함하지 않고, TODO.md에 후속 작업(항목 1.5)으로 남깁니다.

prod 토폴로지는 main module과 함께 검토해야 하는 인프라 결정이므로, 실제로 prod에 Redis를 붙이는 시점에 별도 ADR로 확정합니다. 그때 이 ADR의 2번 결정(헬스체크 격리)도 함께 재검토합니다.

### Consequences

이번 스코프에서는 배포 계약(`backend_runtime_contract.md`)에 Redis 연결 정보(Parameter Store 파라미터 등)를 추가하지 않습니다. `application.yaml`의 Redis 연결 설정은 로컬 기본값(`localhost:6379`)을 그대로 사용해도 되며, prod 환경 변수 주입은 후속 ADR의 몫입니다.

캐싱 기능은 로컬에서는 정상 동작하지만, prod에는 아직 배포되어도 실질적인 캐싱 효과가 없습니다(Redis가 없으므로). 이는 의도된 상태이며, prod 캐싱 효과는 후속 작업(TODO.md 1.5번) 완료 후에 발생합니다.

---

## 2. Actuator 헬스체크 집계에서 Redis 제외

### Decision

`management.health.redis.enabled=false`를 설정하여 `RedisHealthIndicator`가 `/actuator/health`의 집계 상태에 기여하지 않도록 합니다. 캐시는 "있으면 성능에 도움이 되지만 없어도 애플리케이션은 정상 동작"하는 부가 기능으로 취급합니다.

### Consequences

prod Redis가 아직 없는 현재 상태에서도, 그리고 향후 prod Redis가 일시적으로 장애가 나더라도, 이 애플리케이션의 `/actuator/health`는 Redis 상태와 무관하게 `UP`을 유지합니다. main module의 배포 헬스체크 budget(150초)이 Redis 가용성 때문에 실패하는 일이 없습니다.

반대로, Redis 장애가 나도 배포 파이프라인이 이를 감지하지 못합니다. 캐시 미스가 늘어나 DB 부하가 증가하는 상황을 헬스체크로는 알 수 없으므로, prod Redis를 실제로 붙이는 시점에는 별도의 모니터링/알림 수단을 함께 고려해야 합니다.

이 결정으로 `docs/specs/backend_runtime_contract.md` 9절(Health Endpoint Contract)에 Redis 제외 사실을 명시적으로 반영합니다.
