# OAuth 인증 & 로그인/회원가입 API 명세서 (Frontend Interface Spec)

이 문서는 프론트엔드 애플리케이션과 백엔드 간의 OAuth 기반 인증, 회원가입, 및 토큰 갱신 인터페이스 규격을 정의합니다.

---

## 1. 개요 및 인증 흐름 (Authentication Flow)

본 시스템은 카카오, 네이버, 구글 등 소셜 OAuth Provider의 Access Token을 클라이언트로부터 전달받아 검증 후, 자체 플랫폼 토큰(JWT Access Token & Refresh Token)을 발급하는 방식을 채택합니다.

```
[ Frontend ]               [ Backend ]              [ OAuth Provider ]
     |                          |                           |
     |-- 1. OAuth 로그인 요청 -->|                           |
     |   (provider, token)      |-- 2. access token 검증 -->|
     |                          |<-- 3. 유저 프로필 반환 ---|
     |                          |                           |
     |<- 4-A. AUTHENTICATED ----| (기존 회원인 경우)
     |    (accessToken/refresh) |
     |                          |
     |<- 4-B. SIGN_UP_REQUIRED -| (신규 회원인 경우)
     |    (suggestedNickname)   |
     |                          |
     | [신규 회원일 경우 회원가입 진행]
     |-- 5. OAuth 회원가입 요청 ->|
     |   (nickname, agreements, |-- 6. access token 재검증 ->|
     |    preferences, token)   |<-- 7. 유저 프로필 반환 ----|
     |                          | 8. 회원 생성 & DB 저장
     |<- 9. AUTHENTICATED ------|
     |    (accessToken/refresh) |
```

---

## 2. 공통 사항 (Common Specifications)

- **인증 Base URL**: `/api/v1/auth`
- **닉네임 Base URL**: `/api/v1/nicknames`
- **Content-Type**: `application/json`
- **인증 방식**: JWT (JSON Web Token)

### Enum 정의

#### 1) `OAuthProvider`
- `KAKAO`: 카카오 로그인
- `NAVER`: 네이버 로그인
- `GOOGLE`: 구글 로그인

#### 2) `OAuthAuthenticationStatus`
- `AUTHENTICATED`: 로그인 완료 (플랫폼 토큰 발급됨)
- `SIGN_UP_REQUIRED`: 최초 로그인 상태로, 회원가입 절차 필요

#### 3) `MobilityMode` (이동 모드)
- `WHEELCHAIR`: 휠체어
- `WALK`: 보행
- `STROLLER`: 유모차

#### 4) `PriorityFacility` (우선 시설)
- `ELEVATOR`: 엘리베이터
- `ACCESSIBLE_TOILET`: 장애인/장애인용 화장실
- `RAMP`: 경사로
- `PARKING`: 장애인 주차 구역

#### 5) `AvoidCondition` (회피 조건)
- `STAIRS`: 계단
- `STEEP_SLOPE`: 급경사
- `UNEVEN_SURFACE`: 비포장/요철 도로

---

## 3. 약관 동의 비트마스크 (Agreement Mask)

회원가입 시 약관 동의 상태는 64비트 정수(`Long`) 비트마스크로 전달합니다.

| 비트 위치 (Bit) | 십진수 값 | 약관 항목 (`AgreementType`) | 필수 여부 |
|---|---|---|---|
| Bit 0 (`1 << 0`) | `1` | 만 14세 이상 확인 (`AGE_CONFIRMATION`) | **필수** |
| Bit 1 (`1 << 1`) | `2` | 서비스 이용약관 동의 (`TERMS_OF_SERVICE`) | **필수** |
| Bit 2 (`1 << 2`) | `4` | 개인정보 수집 및 이용 동의 (`PERSONAL_INFORMATION_COLLECTION_AND_USE`) | **필수** |
| Bit 3 (`1 << 3`) | `8` | 위치 기반 서비스 이용약관 동의 (`LOCATION_BASED_SERVICE`) | **필수** |
| Bit 4 (`1 << 4`) | `16` | 마케팅 정보 수신 동의 (`MARKETING_INFORMATION_RECEIPT`) | 선택 |

- **필수 동의 마스크 (`REQUIRED_MASK`)**: `1 + 2 + 4 + 8 = 15`
- 프론트엔드는 필수 약관 동의 시 `agreementMask & 15 == 15` 조건을 만족하는 비트연산 값을 전송해야 합니다. (예: 필수약관 전원 동의 + 마케팅 수신 동의 = `15 + 16 = 31`)

---

## 4. API 명세 (API Endpoints)

### 4.1. OAuth 로그인 (`POST /api/v1/auth/oauth/login`)

OAuth Provider의 access token을 검증하고, 회원 여부에 따라 로그인 완료 토큰 또는 회원가입 필요 상태를 반환합니다.

#### 요청 (Request)
- **URL**: `POST /api/v1/auth/oauth/login`
- **Body**:
  ```json
  {
    "provider": "KAKAO",
    "providerAccessToken": "kakao_access_token_string_here"
  }
  ```

| Field | Type | Required | Description | Example |
|---|---|---|---|---|
| `provider` | String (Enum) | Y | OAuth Provider | `"KAKAO"`, `"NAVER"`, `"GOOGLE"` |
| `providerAccessToken` | String | Y | 소셜 Provider에서 발급받은 Access Token | `"kakao_access_token..."` |

#### 응답 (Response) - 200 OK

##### Case A: 기존 회원 로그인 성공 (`AUTHENTICATED`)
```json
{
  "status": "AUTHENTICATED",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "d3b07384-d113-4660-84cf-81b312b9c719",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

##### Case B: 신규 회원 (회원가입 필요, `SIGN_UP_REQUIRED`)
```json
{
  "status": "SIGN_UP_REQUIRED",
  "provider": "KAKAO",
  "suggestedNickname": "함께가는길"
}
```

| Field | Type | Description |
|---|---|---|
| `status` | String (Enum) | `"AUTHENTICATED"` 또는 `"SIGN_UP_REQUIRED"` |
| `accessToken` | String | 플랫폼 JWT Access Token (`AUTHENTICATED` 시 반환) |
| `refreshToken` | String | 플랫폼 JWT Refresh Token (`AUTHENTICATED` 시 반환) |
| `tokenType` | String | 토큰 타입 (`"Bearer"`) |
| `expiresIn` | Number | Access Token 만료까지 남은 시간 (초) |
| `provider` | String (Enum) | 가입이 필요한 OAuth Provider (`SIGN_UP_REQUIRED` 시 반환) |
| `suggestedNickname` | String | 소셜 프로필 기반 닉네임 추천값 (`SIGN_UP_REQUIRED` 시 반환) |

---

### 4.2. 닉네임 사용 가능 여부 (`GET /api/v1/nicknames/{nickname}/availability`)

닉네임 입력 단계에서 사용 가능 여부를 조회합니다. 이 요청은 닉네임을 예약하지 않으므로, 최종 회원가입 요청은 DB 유니크 제약으로 다시 검증됩니다.

#### 응답 (Response) - 200 OK
```json
{
  "available": true
}
```

| Field | Type | Description |
|---|---|---|
| `available` | Boolean | 닉네임 사용 가능 여부 |

- 닉네임은 공백 제거 후 한글·영문·숫자 2~12자여야 하며, 형식이 맞지 않으면 `400 INVALID_REQUEST`를 반환합니다.
- 인증 없이 호출할 수 있습니다.

### 4.3. OAuth 회원가입 (`POST /api/v1/auth/oauth/signup`)

OAuth access token을 재검증 후, 사용자의 닉네임, 약관 동의, 개인화 설정을 저장하고 플랫폼 토큰을 발급합니다.

#### 요청 (Request)
- **URL**: `POST /api/v1/auth/oauth/signup`
- **Body**:
  ```json
  {
    "provider": "KAKAO",
    "providerAccessToken": "kakao_access_token_string_here",
    "nickname": "함께가는길",
    "agreementMask": 15,
    "preferences": {
      "mobilityModes": ["WHEELCHAIR"],
      "informationPreferences": {
        "priorityFacilities": ["ELEVATOR", "RAMP"],
        "avoidConditions": ["STAIRS"]
      }
    }
  }
  ```

| Field | Type | Required | Description | Example |
|---|---|---|---|---|
| `provider` | String (Enum) | Y | OAuth Provider | `"KAKAO"` |
| `providerAccessToken` | String | Y | 소셜 Provider Access Token | `"kakao_access_token..."` |
| `nickname` | String | Y | 사용자 확정 닉네임 (공백제거 후 중복검사) | `"함께가는길"` |
| `agreementMask` | Number (Long) | Y | 약관 동의 비트마스크 (필수약관 동의 시 15 이상) | `15` |
| `preferences` | Object | Y | 사용자 개인화 설정 | 하단 구조 참조 |

##### `preferences` Object 구조
- `mobilityModes` (Array of Enum): `["WHEELCHAIR", "WALK", "STROLLER"]`
- `informationPreferences` (Object):
  - `priorityFacilities` (Array of Enum): `["ELEVATOR", "ACCESSIBLE_TOILET", "RAMP", "PARKING"]`
  - `avoidConditions` (Array of Enum): `["STAIRS", "STEEP_SLOPE", "UNEVEN_SURFACE"]`

#### 응답 (Response) - 200 OK
```json
{
  "status": "AUTHENTICATED",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "d3b07384-d113-4660-84cf-81b312b9c719",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

---

### 4.4. Access Token 갱신 (`POST /api/v1/auth/refresh`)

만료된 Access Token 대신 Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.

#### 요청 (Request)
- **URL**: `POST /api/v1/auth/refresh`
- **Body**:
  ```json
  {
    "refreshToken": "d3b07384-d113-4660-84cf-81b312b9c719"
  }
  ```

| Field | Type | Required | Description |
|---|---|---|---|
| `refreshToken` | String | Y | 로그인/회원가입 시 발급받은 Refresh Token |

#### 응답 (Response) - 200 OK
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 300
}
```

---

## 5. 예외 처리 및 에러 명세 (Error Handling & Exceptions)

모든 실패 응답은 일관된 `ErrorResponse` 규격으로 반환됩니다.

### 표준 에러 응답 구조 (`ErrorResponse`)
```json
{
  "errorCode": "INVALID_OAUTH_TOKEN",
  "errorMessage": "유효하지 않거나 만료된 OAuth access token입니다."
}
```

### 전체 예외 상황 목록

| HTTP Status | `errorCode` | `errorMessage` (사용자 메시지) | 발생 상황 (Cause) | 프론트엔드 대응 가이드 (Handling Action) |
|---|---|---|---|---|
| **400 Bad Request** | `INVALID_REQUEST` | 요청 값이 올바르지 않습니다. | `@Valid` 검증 실패, JSON 파싱 오류, 필수 필드 누락 | 입력값 형식 검증 확인 (Blank, Null 여부 등) |
| **400 Bad Request** | `REQUIRED_AGREEMENTS_NOT_ACCEPTED` | 필수 약관에 모두 동의해야 합니다. | 회원가입 시 필수 약관(비트 0~3, 마스크 15) 미동의 | 약관 동의 체크박스 상태 확인 및 필수 약관 안내 |
| **401 Unauthorized** | `INVALID_OAUTH_TOKEN` | 유효하지 않거나 만료된 OAuth access token입니다. | 소셜 로그인 토큰 만료 또는 유효성 검증 실패 | 소셜 로그인 재시도하여 새 providerAccessToken 획득 |
| **401 Unauthorized** | `INVALID_REFRESH_TOKEN` | 유효하지 않은 리프레시 토큰입니다. | `/refresh` 시 토큰 서명/형식 위변조 | 저장된 토큰 삭제 후 로그인 화면으로 이동 |
| **401 Unauthorized** | `UNKNOWN_REFRESH_TOKEN` | 알 수 없는 리프레시 토큰입니다. | 서버 DB에 존재하지 않는 Refresh Token | 저장된 토큰 삭제 후 로그인 화면으로 이동 |
| **401 Unauthorized** | `EXPIRED_OR_REVOKED_REFRESH_TOKEN` | 만료되었거나 폐기된 리프레시 토큰입니다. | Refresh Token의 만료 기간 초과 또는 폐기됨 | 재로그인 처리 안내 |
| **409 Conflict** | `NICKNAME_ALREADY_IN_USE` | 이미 사용 중인 닉네임입니다. | 회원가입 시 입력한 닉네임이 이미 존재함 | 닉네임 입력란 에러 표시 및 다른 닉네임 입력 요청 |
| **409 Conflict** | `OAUTH_SIGNUP_ALREADY_COMPLETED` | 이미 가입 처리된 OAuth 계정입니다. 로그인 후 다시 시도해주세요. | 이미 가입이 완료된 계정으로 회원가입 시도 (동시 요청 등) | 로그인 화면으로 전환하여 `/oauth/login` 재시도 |
| **503 Service Unavailable** | `OAUTH_PROVIDER_UNAVAILABLE` | OAuth provider에 연결할 수 없습니다. | 소셜 서비스(카카오/네이버/구글) API 서버 장애/통신 실패 | "소셜 인증 서비스 점검 중입니다." 토스트 메시지 안내 |
| **500 Internal Error** | `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. | 백엔드 런타임 미처리 예외 발생 | 잠시 후 다시 시도하도록 토스트 메시지 안내 |

---

## 6. 프론트엔드 연동 팁 & 주의사항 (Frontend Guidelines)

1. **상태값 분기 처리**:
   - `/oauth/login` 호출 후 `status`가 `AUTHENTICATED`이면 곧바로 메인 화면으로 진입시킵니다.
   - `status`가 `SIGN_UP_REQUIRED`이면 약관 동의 및 프로필 입력(닉네임, 이용 모드 등) 폼 화면으로 전환하고, `suggestedNickname`을 닉네임 입력 필드의 기본값으로 채워줍니다.
2. **동시성 및 가입 중복 예외 (`OAUTH_SIGNUP_ALREADY_COMPLETED`)**:
   - 가입 버튼을 연타하거나 이미 가입된 계정이 signup으로 들어올 경우 `409 OAUTH_SIGNUP_ALREADY_COMPLETED`가 리턴됩니다. 프론트는 이 에러를 받으면 "이미 가입된 계정입니다." 안내 후 로그인 API (`/oauth/login`)를 재호출하면 바로 로그인 토큰을 얻을 수 있습니다.
3. **약관 마스크 계산 예시 (JS/TS)**:
   ```typescript
   const AGE_CONFIRMATION = 1 << 0; // 1
   const TERMS_OF_SERVICE = 1 << 1; // 2
   const PERSONAL_INFO = 1 << 2;    // 4
   const LOCATION_SERVICE = 1 << 3; // 8
   const MARKETING = 1 << 4;        // 16

   // 필수 동의 마스크 = 1 | 2 | 4 | 8 = 15
   let mask = 0;
   if (isAgeConfirmed) mask |= AGE_CONFIRMATION;
   if (isTermsAgreed) mask |= TERMS_OF_SERVICE;
   if (isPersonalInfoAgreed) mask |= PERSONAL_INFO;
   if (isLocationAgreed) mask |= LOCATION_SERVICE;
   if (isMarketingAgreed) mask |= MARKETING;
   ```
