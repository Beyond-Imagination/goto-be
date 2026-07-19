---
description: Launch and drive the goto-be Spring Boot server locally
---

# goto-be 로컬 실행 가이드

## 전제 조건

| 항목 | 상태 |
|---|---|
| Java 21 | `/opt/homebrew/opt/openjdk@21` |
| PostgreSQL | AWS RDS (Parameter Store에서 접속 정보 로드) |
| AWS 자격증명 | `.env` 파일 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY) |

## 실행 명령어

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootRun
```

## 설정 로딩 흐름

```
.env (AWS 자격증명)
  → AWS Parameter Store /goto/
    → DB 접속 정보, JWT secret 등
  → application.yaml (기본값)
```

- `.env`는 `bootRun` task에서 자동 로드됨 (build.gradle의 `loadDotenv`)
- Parameter Store 경로: `ap-northeast-2` 리전의 `/goto/` prefix

## 주요 엔드포인트

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/v3/api-docs`
- 로그인: `POST /auth/login`
- 토큰 갱신: `POST /auth/refresh`

## 테스트 실행

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew test
```

테스트는 Testcontainers(PostgreSQL)를 사용하므로 **Docker 필요**, AWS 불필요.

## 작업 완료 시 규칙

작업을 완료하면 반드시 `TODO.md`의 해당 항목을 `- [x]`로 체크한다.

## 기술 스택

- Spring Boot 4.1.0 / Java 21 / Gradle
- PostgreSQL + Flyway (`V1__create_refresh_tokens.sql`)
- Spring Security + JWT (access 60분 / refresh 14일)
- AWS Parameter Store (Spring Cloud AWS 4.0.2)
- Springdoc OpenAPI 3.0.3
