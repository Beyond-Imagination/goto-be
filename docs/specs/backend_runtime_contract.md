# Technical Specification: Backend Runtime Contract

**Document Status:** Accepted  
**Author:** 강민준 (joonamin44@gmail.com)  
**Date:** 2026-07-04

## 1. Purpose

본 문서는 `backend` repository가 main module의 dev runtime에 제공해야 하는 런타임 계약을 정의합니다.

현재 main module dev runtime의 배포 대상은 Lightsail Instance로 고정합니다. 이 문서는 특정 인프라 구현이 아니라 backend submodule이 어떤 Docker image, port, health endpoint, secret/config 주입 방식, datasource/migration 동작을 유지해야 하는지만 다룹니다.

배포 workflow, Docker Compose, Caddy, host preparation, SSH, GHCR credential, image registry 이름, image tag 규칙 같은 인프라 구현은 main module이 소유합니다.

## 2. Scope

### 2.1 In Scope

- Backend Dockerfile과 build context 계약
- Backend `.dockerignore`와 build context exclusion 계약
- Container 내부 HTTP port
- Public health endpoint와 Actuator 노출 범위
- Runtime secret/config 주입 방식
- External RDS 사용 계약
- Flyway startup migration 계약
- Stateless backend container 원칙
- Swagger/OpenAPI public dev surface
- Batch runtime 주의사항

### 2.2 Out of Scope

- Host/cloud instance provisioning
- Docker Compose 파일 작성
- Caddyfile 작성
- GitHub Actions deploy workflow
- GHCR credential 발급/보관
- Image registry/package name 결정
- Image tag 규칙과 multi-platform manifest 정책
- SSH deploy key 운영
- RDS 인프라, 네트워크, 백업, 보안그룹 구성
- Frontend deployment

## 3. Docker Image Contract

main module은 backend submodule 경로를 Docker build context로 사용합니다.

- Build context: `backend/`
- Dockerfile: `backend/Dockerfile`
- Docker ignore file: `backend/.dockerignore`
- Build base image tag: `eclipse-temurin:21-jdk-alpine`
- Runtime base image tag: `eclipse-temurin:21-jre-alpine`
- Runtime image entrypoint: `java -jar /app/app.jar`
- Runtime Java: Java 21 JRE
- Container user: non-root `app`
- Container internal port: `8080`
- Required runtime platform: `linux/amd64`

Backend Dockerfile은 현재 self-contained build 구조를 유지합니다. Docker build 내부에서 Gradle `bootJar`를 수행해 executable jar를 만들고, runtime stage에서 `/app/app.jar`로 실행합니다.

현재 dev/demo 범위에서는 Docker base image를 digest로 pinning하지 않고 위 tag를 그대로 사용합니다. Digest pinning은 추후 supply-chain hardening TODO로 남깁니다.

Dockerfile은 BuildKit syntax directive를 사용합니다.

```dockerfile
# syntax=docker/dockerfile:1
```

Gradle build 단계는 Docker BuildKit cache mount를 사용해 `/root/.gradle` dependency cache를 재사용합니다.

```dockerfile
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon
```

따라서 main module workflow는 Docker Buildx/BuildKit 기반 build를 유지해야 합니다.

`backend/Dockerfile`과 `backend/.dockerignore`는 backend repository가 versioned contract file로 소유합니다. Main module workflow는 이 파일들을 생성하거나 대체하지 않고, backend submodule에 committed 된 파일을 그대로 사용합니다.

`backend/.dockerignore`는 최소한 다음 값을 Docker build context에서 제외해야 합니다.

- `.env`
- `.env.*`
- `.git`
- `.github`
- `.gradle`
- `build`
- `docs`
- `docker-compose*.yml`

`.env.example`은 예시 파일로 허용할 수 있지만, runtime secret이 포함된 env file은 build context에 들어가면 안 됩니다.

현재 main module dev runtime의 Lightsail host CPU는 `Intel(R) Xeon(R) Platinum 8259CL CPU @ 2.50GHz`로 확인되었으므로 x86_64/amd64 기준으로 실행합니다. Backend Dockerfile과 runtime image는 `linux/amd64`에서 정상 build/run 가능해야 합니다.

main module Compose는 backend service를 Docker network 내부에서만 노출합니다. `8080`은 host port로 publish하지 않고, Caddy가 Compose network 안에서 현재 backend service인 `app:8080`으로 접근합니다.

## 4. Configuration Contract

Backend runtime은 별도 `application-dev.yaml`에 의존하지 않습니다. 기본 설정 파일은 `src/main/resources/application.yaml`입니다.

main module dev runtime에서는 `SPRING_PROFILES_ACTIVE`를 설정하지 않습니다. 현재 `application.yaml`은 test profile이 아닌 경우 다음 Parameter Store import를 사용합니다.

```yaml
spring:
  config:
    import: 'optional:aws-parameterstore:/goto/'
```

`optional` import는 로컬 개발 편의를 위한 형태입니다. main module dev runtime에서는 `/goto/` 하위 필수 parameter가 실제로 준비되어 있어야 합니다.

`src/main/resources/application-local-test.yaml`은 로컬/테스트 전용 profile입니다. main module dev runtime은 `local-test` profile을 활성화하지 않으며, 해당 파일의 local datasource, test JWT secret, Parameter Store 비활성화 설정에 의존하지 않습니다.

## 5. Secret Contract

Runtime secret의 source of truth는 GitHub Actions가 아닙니다.

Backend container는 AWS Parameter Store와 host-only env file을 통해 secret을 읽습니다.

### 5.1 Host-Only AWS Env File

main module Compose는 host-only env file을 backend service `env_file`로 주입합니다. 현재 main module의 Lightsail 기준 경로는 `/opt/goto/secrets/aws.env`입니다.

파일 경로, 파일 소유자, 파일 권한, 생성/교체 절차는 main module runbook이 소유합니다. Backend repository는 해당 env file을 통해 주입되는 AWS credential key만 계약으로 둡니다.

허용되는 key는 다음뿐입니다.

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`

`AWS_SESSION_TOKEN`은 장기 access key pair를 사용하는 현재 dev/demo 범위에서는 기본적으로 두지 않습니다.

다음 값은 host-only `aws.env`, Compose `environment`, `deploy.env`, GitHub Actions secret에 두지 않습니다.

- datasource password
- JWT secret
- Tour API service key

### 5.2 Parameter Store Parameters

AWS Parameter Store `/goto/` 하위에는 최소한 다음 parameter가 있어야 합니다.

- `/goto/spring.datasource.url`
- `/goto/spring.datasource.username`
- `/goto/spring.datasource.password`
- `/goto/security.jwt.secret`
- `/goto/tour-api.service-key`

Main module dev deploy workflow는 현재 별도 SSM required-parameter preflight를 수행하지 않습니다. Parameter 누락 또는 오입력은 backend startup 실패와 public `/actuator/health` 검증 실패로 감지합니다.

## 6. Compose Environment Contract

Backend service의 Compose `environment`에는 runtime secret을 넣지 않습니다.

현재 허용되는 non-secret environment는 컨테이너 시간대 일관성을 위한 `TZ=Asia/Seoul` 정도입니다.

## 7. Datasource Contract

main module dev runtime은 Docker Compose 내부 PostgreSQL을 사용하지 않습니다.

Backend application은 이미 준비된 외부 AWS RDS PostgreSQL datasource에 연결합니다. RDS endpoint, username, password는 Parameter Store에서 읽습니다.

RDS 인프라, 네트워크 접근성, 보안그룹, 백업, 가용성 설정은 backend repository의 runtime contract 범위가 아닙니다.

## 8. Flyway Contract

Flyway migration은 Spring Boot application startup 과정에서 자동 실행됩니다.

Migration 실패로 application이 정상 기동하지 못하면 `/actuator/health`가 `UP`이 되지 않아야 하며, main module deploy workflow는 이를 배포 실패로 판단합니다.

Backend repository는 migration 실패를 숨기거나 health success로 위장하지 않습니다.

## 9. Health Endpoint Contract

Backend application은 `spring-boot-starter-actuator`를 포함합니다.

Public health endpoint는 다음 경로입니다.

- `/actuator/health`
- `/actuator/health/**`

Actuator web exposure는 `health`만 허용합니다. Health details는 public response에 노출하지 않습니다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

main module deploy workflow는 public HTTPS endpoint인 `https://${DEV_API_DOMAIN}/actuator/health`가 HTTP 200과 `UP` 상태를 반환해야 배포 성공으로 판단합니다. 이 값은 main module repository variable이며, Compose runtime에서는 Caddy의 `API_DOMAIN` 값으로 전달됩니다.

이 health check는 GitHub Actions runner에서 외부 사용자 경로와 같은 방향으로 수행합니다. 따라서 DNS, TLS certificate, Caddy reverse proxy, backend application startup, datasource aggregate health를 함께 검증합니다. Datasource 연결 실패도 배포 실패로 취급합니다.

Main module deploy workflow의 health check budget은 `5초 간격 x 30회`, 최대 150초입니다. 정상적인 dev/demo 배포에서는 Caddy TLS 준비, Spring Boot startup, Flyway migration, RDS 연결을 포함해 이 시간 안에 `UP` 상태가 되어야 합니다.

## 10. Public Dev API Surface

Dev/demo runtime에서는 Swagger UI와 OpenAPI JSON을 public HTTPS origin에서 접근 가능하게 둡니다.

- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs`
- `/v3/api-docs/**`

운영/상용 단계에서는 Swagger/OpenAPI 공개 여부를 별도 결정으로 재검토합니다.

## 11. Security Contract

Spring Security는 dev/demo runtime에서 다음 경로를 인증 없이 접근 가능하게 유지합니다.

- `/actuator/health`
- `/actuator/health/**`
- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs`
- `/v3/api-docs/**`

그 외 API 인증 정책은 애플리케이션 보안 설계에 따릅니다.

## 12. Stateless Container Contract

Backend container는 stateless runtime으로 취급합니다.

Backend service에는 Docker named volume 또는 host bind mount를 두지 않습니다. Application state의 source of truth는 외부 RDS와 AWS Parameter Store입니다.

향후 사용자 업로드 파일이나 장기 보관 파일이 필요해지면 backend container volume이 아니라 S3 같은 외부 object storage를 별도 결정으로 검토합니다.

## 13. Batch Runtime Contract

Spring Boot 4.x는 `spring-boot-starter-batch`만으로는 인메모리 `ResourcelessJobRepository`가 기본입니다. Job 메타데이터를 RDS `batch_job_*` 테이블에 영속화하려면 `spring-boot-starter-batch-jdbc`를 함께 사용해야 합니다. 스키마는 Flyway `V3__create_spring_batch_tables.sql`이 소유하므로 `spring.batch.jdbc.initialize-schema=never`를 유지합니다.

main module dev runtime에서도 `goto.batch.initial-load.auto-run-enabled=true`를 유지합니다.

### 13.1 Initial Load Job Scope

`tourApiInitialLoadJob`은 **목록(base) step만** 실행합니다. `areaBasedList2`로 전국 장소 목록을 페이징 수집하고 `places`에 upsert한 뒤 `COMPLETED`로 종료합니다.

상세 보강(`detailCommon2`, `detailWithTour2`, `detailIntro2`)은 초기 적재 job에 포함하지 않습니다. `overview`, `homepage`, `place_bf_info` 등 detail 의존 필드는 `tourApiIncrementalSyncJob`의 Lazy Detail Fetch step과 증분 processor의 Eager Fetch로 점진 보강합니다.

### 13.2 Initial Load Completion & Scheduler Guard

초기 적재 완료 여부는 설정값이 아니라 RDS에 보존된 Spring Batch metadata의 `tourApiInitialLoadJob` `COMPLETED` 이력으로 판단합니다. base step만 완료되어도 `COMPLETED`이므로, 이후 기동 시 자동 초기 적재는 스킵되고 03:00 증분 스케줄러 가드도 통과합니다.

`goto.batch.initial-load.auto-run-enabled`는 완료 여부가 아니라 자동 실행 on/off 스위치입니다.

### 13.3 Incremental Sync Schedule

증분 동기화 스케줄은 KST 기준 매일 03:00 실행을 전제로 합니다. Lazy Detail Fetch step은 실행당 최대 `tour-api.detail-quota`(기본 250)건의 미보강 장소만 처리합니다.

### 13.4 Deploy & Health Interaction

`TourApiInitialLoadRunner`는 `ApplicationReadyEvent` 이후 `JobOperator.start()`로 초기 적재 job을 **동기** 실행합니다. Spring Batch job이 실행 중이면 `/actuator/health`가 `OUT_OF_SERVICE`(HTTP 503)로 응답할 수 있습니다.

초기 적재에서 detail step을 제거하면 기동 시 blocking 시간과 Tour API detail 호출 부하가 줄어 dev deploy health 검증이 개선됩니다. 다만 빈 DB 최초 1회 base 적재만으로도 수 분이 걸릴 수 있어, main module deploy workflow의 health check budget(5초 간격 × 30회, 최대 150초)을 초과하면 1회 배포 실패가 남을 수 있습니다. 한 번 `COMPLETED`가 기록되면 이후 재배포는 초기 적재를 스킵합니다.

배포 중 batch job drain/lock은 backend runtime contract에 포함하지 않습니다. 현재 dev/demo 운영에서는 초기 적재 중이거나 03:00 KST 증분 배치 시간대에는 배포하지 않는 수동 운영 원칙을 따릅니다.

### 13.5 Known Residual Risks

다음 리스크는 본 변경만으로 완전히 제거되지 않습니다.

- **동기 초기 적재**: base-only여도 `ApplicationReadyEvent` 핸들러가 job 완료까지 블로킹합니다.
- **빈 DB 첫 배포**: base 적재가 150초를 넘기면 public health 검증이 실패할 수 있습니다.
- **detail 데이터 공백**: 초기 적재 직후 ~ 첫 03:00 KST 증분 job 전까지 `overview`, `homepage`, `place_bf_info`, 검색 API `bfDetails`가 비어 있을 수 있습니다.
- **보강 속도**: 미보강 backlog는 일 250건 quota로 소화되므로 전체 장소 수가 많으면 완전 보강까지 수일~수개월이 걸릴 수 있습니다.
- **FAILED 이력 재시도**: `COMPLETED`가 없으면 재기동마다 base 적재를 재시도합니다. upsert는 idempotent하지만 Tour API 목록 호출 비용이 발생합니다.
- **03:00 배포 충돌**: 증분 job 실행 중 배포 시 동일한 health 503 패턴이 재현될 수 있습니다.

## 14. Logging Contract

Application log에는 runtime secret을 출력하지 않습니다.

특히 다음 값은 로그에 남기지 않습니다.

- AWS access key
- datasource URL에 포함될 수 있는 credential
- datasource password
- JWT secret
- Tour API service key
- Parameter Store value

main module deploy workflow는 remote deploy 실패 또는 health check 실패 시 backend `app` log와 reverse proxy `caddy` log tail을 GitHub Actions log에 남길 수 있습니다. 따라서 backend application이 secret을 로그로 출력하지 않는 것이 runtime contract의 일부입니다.

## 15. Change Management

이 contract에 영향을 주는 backend 변경은 main module deployment와 함께 검토해야 합니다.

예를 들어 다음 변경은 contract 변경입니다.

- Dockerfile 위치 또는 build context 변경
- Docker base image tag 변경 또는 digest pinning 도입
- Dockerfile BuildKit syntax directive 또는 Gradle cache mount 제거
- `.dockerignore` 제거 또는 `.env`, `.env.*`, `docs`, `build` exclusion 변경
- Container internal port 변경
- `/actuator/health` 경로 또는 Actuator exposure 변경
- Parameter Store path 변경
- 필수 runtime parameter 추가/삭제
- `SPRING_PROFILES_ACTIVE` 전제 추가
- Flyway startup migration 동작 변경
- Backend container local volume 필요성 추가
- Swagger/OpenAPI public 경로 변경

Contract 변경이 필요하면 이 spec과 관련 ADR을 먼저 갱신한 뒤 main module의 Compose, Caddy, deploy workflow, runbook을 함께 갱신합니다.
