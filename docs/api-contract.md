# 쿠폰 API 계약 (API Contract)

> **실패도 계약이다.**
> 성공 응답만 명세하는 API는 절반짜리다. 클라이언트가 실제로 분기하는 지점은 대부분 실패 쪽이고,
> 실패 응답의 모양(HTTP status / 식별 코드 / 본문 스키마)이 흔들리면 클라이언트는 문자열 메시지를 파싱하기 시작한다.
> 이 문서는 이 서비스의 **성공과 실패를 같은 무게로** 고정한다.

- Base URL: `http://localhost:8080`
- 인증: 없음. 호출자는 `X-User-Id: <Long>` 헤더로 자신을 밝힌다. (학습용 스텁, 실제 인증 아님)
- 성공 본문: `application/json`
- 실패 본문: `application/problem+json` (RFC 9457 Problem Details)

---

## 1. 실패 계약 (Error Contract)

모든 도메인 실패는 `DomainException`을 상속하고,
`GlobalExceptionHandler`가 이를 단일 형태의 `ProblemDetail`로 직렬화한다.

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "about:blank",
  "title": "Sold Out",
  "status": 409,
  "detail": "쿠폰이 매진되었습니다",
  "instance": "/api/coupons/1/issue",
  "code": "SOLD_OUT"
}
```

| 필드 | 출처 | 클라이언트 사용법 |
| --- | --- | --- |
| `status` | `DomainException.httpStatus` | 재시도/인증/입력 오류의 대분류 판단 |
| `code` | `DomainException.code` | **분기의 유일한 기준.** 안정적인 식별자로 취급한다 |
| `detail` | 예외 메시지 | 사람이 읽는 문구. 언제든 바뀔 수 있으므로 파싱 금지 |
| `title` | `code`를 사람이 읽게 변환한 값 | 로그/디버깅용 |
| `instance` | 요청 URI | 로그 상관관계 추적용 |

> 계약의 핵심: **`code`는 불변, `detail`은 가변.**
> 클라이언트가 `detail` 문자열로 분기하면 문구 수정 한 번에 깨진다.

### 1.1 도메인 에러 코드 전체 목록

| `code` | HTTP | 발생 지점 | 의미 |
| --- | --- | --- | --- |
| `COUPON_NOT_FOUND` | 404 | 발급 | 해당 `couponId`의 쿠폰 행사가 없음 |
| `NOT_STARTED` | 409 | 발급 | `startsAt` 이전이라 아직 발급 개시 전 |
| `SOLD_OUT` | 409 | 발급 | `issuedQuantity >= totalQuantity` |
| `ALREADY_ISSUED` | 409 | 발급 | 같은 유저가 같은 쿠폰을 이미 발급받음 |
| `ISSUANCE_NOT_FOUND` | 404 | 사용 | 해당 `issuanceId`의 발급 내역이 없음 |
| `NOT_OWNER` | 403 | 사용 | 발급 내역의 주인이 `X-User-Id`와 다름 |
| `ALREADY_USED` | 409 | 사용 | 이미 사용 완료된 발급 내역 |
| `EXPIRED` | 409 | 사용 | 만료된 발급 내역 (`expiresAt` 도달) |

**상태코드 선택 근거**

- `404` — 리소스 자체가 없다. 다시 시도해도 같다.
- `403` — 리소스는 있으나 호출자에게 권한이 없다.
  존재 여부를 이미 노출하므로, 존재를 숨겨야 하는 서비스라면 `404`로 통일하는 선택도 가능하다. 여기서는 학습 목적상 구분을 유지한다.
- `409` — 요청 자체는 유효하나 **리소스의 현재 상태**가 그 요청을 허용하지 않는다.
  매진/중복발급/만료/사용완료는 전부 "지금은 안 된다"이므로 400이 아니라 409다.

### 1.2 프레임워크 실패

`spring.mvc.problemdetails.enabled=true` 이므로 Spring 기본 예외도 같은 `problem+json` 형태로 나간다.
다만 도메인 예외가 아니므로 **`code` 필드가 없다.**

| 상황 | HTTP | `code` |
| --- | --- | --- |
| `X-User-Id` 헤더 누락 | 400 | (없음) |
| 본문 JSON 파싱 실패 / 타입 불일치 | 400 | (없음) |
| 경로 변수가 숫자가 아님 | 400 | (없음) |
| 존재하지 않는 경로 | 404 | (없음) |
| 허용되지 않은 메서드 | 405 | (없음) |
| 처리되지 않은 예외 | 500 | (없음) |

> 클라이언트는 `code` 부재를 "도메인 실패가 아님 = 요청 자체가 잘못되었거나 서버 장애"로 읽으면 된다.

---

## 2. 엔드포인트

### 2.1 쿠폰 행사 생성

```
POST /api/coupons
Content-Type: application/json
```

**Request**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | string | ✅ | – | 쿠폰 행사명 (최대 80자) |
| `totalQuantity` | int | ❌ | `5000` | 총 발급 수량 |
| `validityDays` | int | ❌ | `7` | 발급 시점 기준 유효 일수 |
| `startsAt` | date-time | ❌ | `null` | 발급 개시 시각. `null`이면 즉시 개시 |

```json
{ "name": "여름 특가 쿠폰", "totalQuantity": 100, "validityDays": 7, "startsAt": "2026-09-01T00:00:00" }
```

**Response `201 Created`**

```json
{
  "id": 1,
  "name": "여름 특가 쿠폰",
  "totalQuantity": 100,
  "issuedQuantity": 0,
  "validityDays": 7,
  "startsAt": "2026-09-01T00:00:00",
  "createdAt": "2026-08-27T10:00:00"
}
```

**실패**

| HTTP | `code` | 조건 |
| --- | --- | --- |
| 400 | (없음) | 본문 파싱 실패 / `name` 누락 |

---

### 2.2 쿠폰 발급

```
POST /api/coupons/{couponId}/issue
X-User-Id: 1
```

**Response `200 OK`**

```json
{
  "id": 10,
  "userId": 1,
  "couponId": 1,
  "status": "ISSUED",
  "issuedAt": "2026-08-27T10:05:00",
  "expiresAt": "2026-09-03T10:05:00",
  "usedAt": null
}
```

**실패**

| HTTP | `code` | 조건 |
| --- | --- | --- |
| 400 | (없음) | `X-User-Id` 누락, `couponId`가 숫자 아님 |
| 404 | `COUPON_NOT_FOUND` | 쿠폰 행사 없음 |
| 409 | `NOT_STARTED` | `now < startsAt` |
| 409 | `SOLD_OUT` | 재고 소진 |
| 409 | `ALREADY_ISSUED` | 동일 유저 중복 발급 |

> 판정 순서는 **존재 → 개시 → 재고 → 중복** 으로 고정한다.
> 여러 실패가 동시에 성립할 때 어떤 `code`가 나오는지는 계약의 일부다. 예: 매진된 쿠폰을 이미 발급받은 유저가 다시 요청하면 `SOLD_OUT`이 나온다.

---

### 2.3 쿠폰 사용

```
POST /api/issuances/{issuanceId}/use
X-User-Id: 1
```

**Response `200 OK`**

```json
{
  "id": 10,
  "userId": 1,
  "couponId": 1,
  "status": "USED",
  "issuedAt": "2026-08-27T10:05:00",
  "expiresAt": "2026-09-03T10:05:00",
  "usedAt": "2026-08-27T11:00:00"
}
```

**실패**

| HTTP | `code` | 조건 |
| --- | --- | --- |
| 400 | (없음) | `X-User-Id` 누락, `issuanceId`가 숫자 아님 |
| 404 | `ISSUANCE_NOT_FOUND` | 발급 내역 없음 |
| 403 | `NOT_OWNER` | 발급 내역의 `userId != X-User-Id` |
| 409 | `ALREADY_USED` | `status == USED` |
| 409 | `EXPIRED` | `status == EXPIRED` 또는 `now >= expiresAt` |

> 판정 순서: **존재 → 소유 → 상태 → 만료.**
> 소유권을 상태보다 먼저 본다. 남의 쿠폰에 대해 "이미 사용됨" 같은 상태 정보를 흘리지 않기 위해서다.

> 만료는 두 경로로 나온다. 저장된 `status == EXPIRED`, 그리고 `ISSUED`이지만 `expiresAt`이 지난 경우.
> 후자를 조회 시점에 계산하므로, 만료 배치가 없어도 사용 시점 판정은 정확하다.

---

### 2.4 내 발급 내역 조회

```
GET /api/users/me/issuances
X-User-Id: 1
```

**Response `200 OK`** — `issuedAt` 내림차순 배열. 내역이 없으면 `[]` (404 아님).

```json
[
  {
    "id": 10,
    "userId": 1,
    "couponId": 1,
    "status": "ISSUED",
    "issuedAt": "2026-08-27T10:05:00",
    "expiresAt": "2026-09-03T10:05:00",
    "usedAt": null
  }
]
```

**실패**

| HTTP | `code` | 조건 |
| --- | --- | --- |
| 400 | (없음) | `X-User-Id` 누락 |

> 빈 컬렉션은 실패가 아니다. `200 []`로 응답한다.
> 여기서 404를 주면 클라이언트는 "유저 없음"과 "내역 없음"을 구분할 수 없게 된다.

---

## 3. 리소스 표현

### CouponResponse

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | long | |
| `name` | string | |
| `totalQuantity` | int | |
| `issuedQuantity` | int | |
| `validityDays` | int | |
| `startsAt` | date-time \| null | `null`이면 즉시 개시 |
| `createdAt` | date-time | |

### IssuanceResponse

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `id` | long | |
| `userId` | long | |
| `couponId` | long | |
| `status` | `ISSUED` \| `USED` \| `EXPIRED` | 문자열 enum |
| `issuedAt` | date-time | |
| `expiresAt` | date-time | |
| `usedAt` | date-time \| null | `USED`가 아니면 `null` |

- 날짜/시간은 모두 `LocalDateTime` — 타임존 없는 ISO-8601 문자열(`2026-08-27T10:05:00`)이다. 서버 로컬 타임존 기준이며, 다중 리전에서는 `Instant`/`OffsetDateTime`로 바꿔야 한다.
- `status`는 이름으로 직렬화된다(`@Enumerated(EnumType.STRING)`). 순서 변경은 안전하지만 **이름 변경은 파괴적 변경**이다.

---

## 4. 아직 계약이 아닌 것 (Known Gaps)

정직하게 남겨둔다. 아래는 현재 코드가 **보장하지 않는다.**

1. **요청 값 검증이 없다.** `name`이 빈 문자열이거나 `totalQuantity`가 음수여도 201이 나간다.
   → `spring-boot-starter-validation` + `@Valid`, 그리고 `MethodArgumentNotValidException` 핸들러로 `VALIDATION_FAILED` 코드를 추가해야 한다.
2. **동시성 보장이 없다.** `issuedQuantity++`는 락 없이 수행되므로, 동시 요청 시 `totalQuantity`를 넘겨 발급될 수 있다(oversell).
   유니크 제약 `uk_issuance_user_coupon` 덕에 **중복 발급만은** DB가 막지만, 그 경우 `ALREADY_ISSUED`가 아니라 `DataIntegrityViolationException` → 500이 나간다.
   → 비관적 락 / 원자적 UPDATE / 제약 위반의 `ALREADY_ISSUED` 매핑이 필요하다.
3. **만료 배치가 없다.** `IssuanceStatus.EXPIRED`는 어디서도 저장되지 않는다.
   조회 응답의 `status`는 만료 시각이 지나도 `ISSUED`로 보인다. 사용 시점에만 `expiresAt`으로 판정한다.
4. **`X-User-Id`는 인증이 아니다.** 아무 값이나 넣으면 그 유저가 된다. 실제 서비스라면 토큰에서 주체를 꺼내야 한다.
5. **페이징이 없다.** `GET /api/users/me/issuances`는 전체를 반환한다.
