# 수동 테스트 방법

본 문서는 cdlab-chat 의 동작을 수동으로 검증하는 절차를 도메인별로 정리한다.
도메인이 늘어나면 섹션을 아래로 추가한다.

## 사전 조건

```bash
# 1. PostgreSQL 컨테이너 기동
docker compose up -d

# 2. 애플리케이션 기동
./gradlew bootRun
```

애플리케이션 기본 포트는 `8080`.

---

## common

### Healthcheck — `GET /api/health`

DB roundtrip(`SELECT 1`) 후 정수 `1` 을 응답한다.
일반 헬스체크는 DB 호출을 하지 않지만, 본 엔드포인트는 controller-service-repository 계층 구조 검증을 1차 목적으로 하기 때문에 의도적으로 roundtrip 을 포함한다.

```bash
curl -i http://localhost:8080/api/health
```

기대 응답:

```
HTTP/1.1 200
Content-Type: application/json
Content-Length: 1

1
```

검증 포인트:

- HTTP 200
- 응답 본문 `1` (DB 가 `SELECT 1` 을 정상 처리)
- 애플리케이션 로그에 SQL 실행 흔적 (Hibernate SQL 로깅 활성화 시)

---

## 인증 안내 (이하 모든 도메인 공통)

`/api/health` 외 모든 엔드포인트는 `X-User-Id` 헤더로 mock 인증된다.
초기 시드 사용자 (V1__init_schema.sql):

| id | name  | 용도                                               |
|----|-------|----------------------------------------------------|
| 1  | user1 | 세션 creator                                       |
| 2  | user2 | 세션 joiner                                        |
| 3  | user3 | 비멤버 (FORBIDDEN_PARTICIPANT 음성 케이스 재현용) |

이하 예시는 user1(creator) ↔ user2(joiner) 1:1 세션을 가정한다.

---

## session

### 세션 생성 — `POST /api/sessions`

user1 이 user2 와의 1:1 세션을 생성한다. 초기 status 는 `SUSPENDED`
(웹소켓 미도입 단계에서는 양쪽 연결을 판단할 수 없으므로 ACTIVE 전이 없음).

```bash
curl -i -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"joinerId": 2}'
```

기대 응답 (201/200, 본문 예시):

```json
{
  "data": {
    "id": 1,
    "creatorId": 1,
    "joinerId": 2,
    "status": "SUSPENDED",
    "createdAt": "2026-04-29T..."
  }
}
```

검증 포인트:

- `data.id` 가 채워져 있음 → 다음 단계에서 사용
- `data.status == "SUSPENDED"`
- DB 확인: `SELECT * FROM sessions;` 에 row 1건

#### 음성 케이스

자기 자신과의 세션 → `400 SELF_SESSION_NOT_ALLOWED`:

```bash
curl -i -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"joinerId": 1}'
```

존재하지 않는 joiner → `404 USER_NOT_FOUND`:

```bash
curl -i -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"joinerId": 9999}'
```

이미 진행 중인 동일 페어 세션이 있을 때 재생성 → `409 SESSION_ALREADY_EXISTS`:

```bash
# 위의 정상 생성 직후 같은 요청 반복
curl -i -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"joinerId": 2}'
```

순서를 바꾸어도 (user2 → user1) 동일 페어로 인식되어 거부되어야 함:

```bash
curl -i -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2" \
  -d '{"joinerId": 1}'
```

### 세션 목록 조회 — `GET /api/sessions`

호출자에게 보이는 세션 전체. 가시성 규칙:

- 멤버이면서 자신이 명시적으로 LEAVE(=`/end` 호출) 하지 않은 세션
- ENDED 된 세션도 자신이 LEAVE 하지 않았다면 히스토리로 노출
- 정렬: `created_at DESC`

```bash
curl -i http://localhost:8080/api/sessions \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": [
    {
      "id": 1,
      "creatorId": 1,
      "joinerId": 2,
      "status": "SUSPENDED",
      "createdAt": "2026-04-30T...",
      "endedAt": null
    }
  ]
}
```

검증 포인트:

- 위 세션 생성 직후 user1, user2 모두 동일하게 1건 노출
- 한쪽이 `/end` 호출 후 → 호출자 목록에서는 사라지고, 상대 목록에서는 status=ENDED 로 노출
- 양쪽 모두 `/end` 호출 후 → 양쪽 모두 목록에서 사라짐 (DB 의 row 는 보존, `creator_left_at`/`joiner_left_at` 모두 NOT NULL)

### 세션 종료 — `POST /api/sessions/{sessionId}/end`

호출자가 세션을 명시적으로 떠난다. 내부 동작:

- LEAVE 이벤트 발행 (client-emitted, `clientEventId` 필수)
- `sessions.{호출자쪽}_left_at = now()` 세팅
- 첫 종료 전이일 경우에 한해 서버가 SESSION_ENDED 이벤트 추가 발행 (server-emitted, `client_event_id IS NULL`)

```bash
SESSION_ID=1
CLIENT_EVENT_ID=$(uuidgen)

curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{ \"clientEventId\": \"${CLIENT_EVENT_ID}\" }"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "status": "ENDED",
    "endedAt": "2026-04-30T...",
    "leftAt": "2026-04-30T..."
  }
}
```

검증 포인트:

- HTTP 200, `status == "ENDED"`, `endedAt` / `leftAt` 채워짐
- DB 확인:
  - `sessions` row → `status='ENDED'`, `creator_left_at IS NOT NULL` (호출자가 user1 인 경우), `joiner_left_at IS NULL`
  - `events` 에 row 2건 추가:
    - `event_type='LEAVE'`, `client_event_id=${CLIENT_EVENT_ID}`, `payload={}`
    - `event_type='SESSION_ENDED'`, `client_event_id IS NULL`, `payload` JSONB 에 `triggeredByUserId`, `reason='LEAVE'`
- GET `/api/sessions` 호출 시 호출자(user1) 응답에서 사라짐
- 상대(user2) 호출 시에는 같은 세션이 `status='ENDED'` 로 보임

#### 멱등 — 동일 clientEventId 재시도

```bash
# 같은 CLIENT_EVENT_ID 로 재호출
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{ \"clientEventId\": \"${CLIENT_EVENT_ID}\" }"
```

검증 포인트:

- HTTP 200, 응답 동일
- DB: `events` row 수 증가 없음 (LEAVE 도 SESSION_ENDED 도 추가 안 됨)
- `creator_left_at` / `ended_at` 시각 불변

#### 두 번째 사용자가 뒤늦게 /end (이미 ENDED 상태)

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2" \
  -d "{ \"clientEventId\": \"$(uuidgen)\" }"
```

검증 포인트:

- HTTP 200 — ENDED 세션이지만 LEAVE 는 자기 가시성 정리를 위해 예외적으로 허용됨
- DB: `joiner_left_at` 채워짐, `events` 에 LEAVE row 1건 추가, **SESSION_ENDED 는 추가되지 않음** (라이프사이클 신호 중복 방지)
- GET `/api/sessions` 호출 시 user2 응답에서도 사라짐 → 양쪽 모두 비어 있음

#### 음성 케이스

비멤버 호출 → `403 FORBIDDEN_PARTICIPANT`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3" \
  -d "{ \"clientEventId\": \"$(uuidgen)\" }"
```

존재하지 않는 세션 → `404 SESSION_NOT_FOUND`.

`clientEventId` 누락 → `400 INVALID_REQUEST` (Bean Validation).

---

## connection

### 사전 컨텍스트

WebSocket 미채택. `connect`/`disconnect` 는 운영 환경의 WebSocket open/close 핸들러를 REST 로 시뮬레이션한다 (design-decision.md "웹소켓 미구현" 참조). 클라이언트는 JOIN/RECONNECT/DISCONNECT 를 명시 발행하지 않으며, 서버가 events 조회로 자동 분기 발행한다.

| EventType | client-emitted | 발행 시점 | client_event_id |
|-----------|----------------|-----------|------------------|
| JOIN | X (server) | 첫 connect | NULL |
| RECONNECT | X (server) | 후속 connect | NULL |
| DISCONNECT | X (server) | disconnect (online 상태에서) | NULL |

이하 예시는 `SESSION_ID` 가 SUSPENDED 세션이라고 가정한다 (음성 케이스에서 ENDED 검증).

### 첫 connect — `POST /api/sessions/{sessionId}/connect`

events 에 `(sessionId, userId, JOIN)` row 가 없으면 서버가 JOIN 을 발행한다.

```bash
SESSION_ID=1

curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/connect" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "userId": 1,
    "eventType": "JOIN",
    "eventId": ...,
    "createdAt": "2026-04-30T..."
  }
}
```

검증 포인트:

- `data.eventType == "JOIN"`
- DB 확인: `events` 에 row 1건 추가 → `event_type='JOIN'`, `user_id=1`, `client_event_id IS NULL`, `payload={}`
- in-memory presence (MockSessionManager) 는 외부 관찰 불가 — `presence 응답 부착` 단계 도입 후 GET `/api/sessions` 응답으로 간접 검증

### 후속 connect — RECONNECT 발행

같은 `(sessionId, userId)` 로 connect 재호출.

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/connect" \
  -H "X-User-Id: 1"
```

검증 포인트:

- `data.eventType == "RECONNECT"`
- DB 확인: `events` 에 RECONNECT row 추가, 기존 JOIN row 는 그대로 유지 (events 는 immutable)
- 몇 번을 재호출해도 두 번째 이후는 항상 RECONNECT — events 의 JOIN row 가 분기 기준이므로 멱등 키 없이도 자연 분기

### disconnect — `POST /api/sessions/{sessionId}/disconnect`

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/disconnect" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "userId": 1,
    "eventType": "DISCONNECT",
    "eventId": ...,
    "createdAt": "2026-04-30T...",
    "noop": false
  }
}
```

검증 포인트:

- `data.eventType == "DISCONNECT"`, `data.noop == false`
- DB 확인: `events` 에 DISCONNECT row 추가, `client_event_id IS NULL`
- 후속 connect 호출 시 JOIN 이 아니라 RECONNECT 가 발행됨 — events 의 JOIN row 가 이미 있기 때문 (disconnect 가 그것을 지우지 않음)

#### 멱등 — offline 상태 disconnect 재호출

이미 offline 인 user 가 disconnect 를 재호출.

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/disconnect" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "userId": 1,
    "eventType": "DISCONNECT",
    "eventId": null,
    "createdAt": null,
    "noop": true
  }
}
```

검증 포인트:

- HTTP 200, `data.noop == true`, `data.eventId / createdAt == null`
- DB 확인: `events` 에 row **추가되지 않음** — stale DISCONNECT 누적 방지 (멱등성)

### 음성 케이스

비멤버 호출 → `403 FORBIDDEN_PARTICIPANT`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/connect" \
  -H "X-User-Id: 3"
```

존재하지 않는 세션 → `404 SESSION_NOT_FOUND`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/9999/connect" \
  -H "X-User-Id: 1"
```

ENDED 세션 connect/disconnect → `409 SESSION_ENDED`:

```bash
# 먼저 SESSION_ID 를 /end 로 종료시킨 뒤
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/connect" \
  -H "X-User-Id: 1"
```

검증 포인트:

- HTTP 409, errorCode `SESSION_ENDED`
- "끝난 세션엔 누구도 online 아님" invariant 와 일관 — `LeaveHandler` 의 `clearSession` 으로 in-memory presence 도 동시 정리됨 (presence 조회로 직접 관찰 가능, 아래 `presence` 절 참조)

### presence 조회 — `GET /api/sessions/{sessionId}/presence`

세션 멤버가 해당 세션의 양 사용자 online 상태를 조회한다. `MockSessionManager` 의 in-memory 스냅샷을 그대로 노출 — 휘발성, 캐시 부적합.

세션 정보 응답(`/api/sessions`) 과 별도 endpoint 로 분리 — presence 는 도메인 라이프사이클이 아닌 connection lifetime(WebSocket 시뮬레이션) 정보이며, 응답을 섞으면 책임이 흐려져 평가 시점의 설명 단위가 어긋남.

```bash
SESSION_ID=1

curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/presence" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "creatorId": 1,
    "joinerId": 2,
    "creatorOnline": true,
    "joinerOnline": false,
    "queriedAt": "2026-04-30T..."
  }
}
```

검증 포인트:

- `creatorOnline` / `joinerOnline` 이 직전 connect/disconnect 결과와 일치
- `queriedAt` 은 호출 시각 — 같은 상태에서도 호출마다 갱신됨 (스냅샷 신호)
- DB 호출 없음 — in-memory 조회만 (Hibernate SQL 로깅에 SELECT 1건만 보임: `findById`)

#### 시나리오 — 양쪽 connect/disconnect 추적

권장 검증 절차:

1. 세션 생성 직후 `presence` 호출 → `creatorOnline=false, joinerOnline=false` (아직 connect 호출 X)
2. user1 connect → presence: `creatorOnline=true, joinerOnline=false`
3. user2 connect → presence: `creatorOnline=true, joinerOnline=true`
4. user1 disconnect → presence: `creatorOnline=false, joinerOnline=true`
5. user1 connect (RECONNECT) → presence: `creatorOnline=true, joinerOnline=true`

#### ENDED 세션 — `clearSession` invariant 직접 관찰

read 는 ENDED 세션도 허용 (timeline/messages/list 와 일관). `clearSession` 효과로 두 값 모두 false 로 자연 반환.

```bash
# 1) user1, user2 모두 connect → presence 둘 다 true 확인
# 2) user1 이 /end 호출 → SESSION_ENDED server-emit + clearSession
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/end" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{ \"clientEventId\": \"$(uuidgen)\" }"

# 3) presence 조회 — 200, 둘 다 false
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/presence" \
  -H "X-User-Id: 2"
```

검증 포인트:

- HTTP 200 (read 는 ENDED 허용)
- `creatorOnline=false`, `joinerOnline=false` — `clearSession` 으로 bucket 이 제거됐기 때문
- "끝난 세션엔 누구도 online 아님" invariant 가 응답으로 직접 관찰됨

#### 음성 케이스

비멤버 호출 → `403 FORBIDDEN_PARTICIPANT`:

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/presence" \
  -H "X-User-Id: 3"
```

존재하지 않는 세션 → `404 SESSION_NOT_FOUND`.

---

## event

이하 예시에서 `SESSION_ID` 는 위에서 생성한 세션의 `data.id`,
`CLIENT_EVENT_ID` 는 클라이언트가 발급한 UUID (멱등성 키) 라고 가정한다.
UUID 생성 예: `uuidgen` (macOS) 또는 `python -c 'import uuid; print(uuid.uuid4())'`.

### MESSAGE_CREATED — `POST /api/sessions/{sessionId}/events`

user1 이 메시지를 작성한다.

```bash
SESSION_ID=1
CLIENT_EVENT_ID=$(uuidgen)

curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_CREATED\",
    \"clientEventId\": \"${CLIENT_EVENT_ID}\",
    \"payload\": { \"content\": \"안녕하세요\" }
  }"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "eventId": 1,
    "sessionId": 1,
    "eventType": "MESSAGE_CREATED",
    "payload": {
      "content": "안녕하세요",
      "messageId": 1
    },
    "clientEventId": "<CLIENT_EVENT_ID>",
    "createdAt": "2026-04-29T...",
    "messageId": 1
  }
}
```

검증 포인트:

- `data.messageId` 가 `data` 직속 필드(=`payload` 와 동렬, payload 외부)로 채워짐
  — 클라이언트가 payload 스키마를 모르고도 후속 EDIT/DELETE 에 바로 사용할 수 있도록 평탄화
- `data.payload.messageId` 도 동일 값으로 enrich 되어 있음 (events 테이블에 저장되는 server-enriched payload)
- DB 확인:
  - `SELECT * FROM events;` → row 1건, `payload` JSONB 에 `content` 와 `messageId` 둘 다 존재
  - `SELECT * FROM messages;` → row 1건, `content == '안녕하세요'`, `deleted_at IS NULL`, `edited_at IS NULL`

#### 멱등 재시도

같은 `clientEventId` 로 동일 요청을 한 번 더 보낸다.

```bash
# 위 요청을 같은 CLIENT_EVENT_ID 로 그대로 재실행
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_CREATED\",
    \"clientEventId\": \"${CLIENT_EVENT_ID}\",
    \"payload\": { \"content\": \"안녕하세요\" }
  }"
```

검증 포인트:

- HTTP 200, 응답 `data` 가 첫 호출과 **동일** (`eventId`, `messageId` 모두 동일)
- DB 확인: `events`, `messages` row 수가 1건에서 늘어나지 않음

#### 음성 케이스

빈 content → `400 INVALID_EVENT_PAYLOAD`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_CREATED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"content\": \"   \" }
  }"
```

세션 멤버가 아닌 사용자 → `403 FORBIDDEN_PARTICIPANT`
(creator=1, joiner=2 인 세션에 비멤버 user3 으로 호출):

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3" \
  -d "{
    \"eventType\": \"MESSAGE_CREATED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"content\": \"hi\" }
  }"
```

server-emitted 이벤트 타입 직접 호출 → `400 UNSUPPORTED_EVENT_TYPE`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"DISCONNECT\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": {}
  }"
```

### MESSAGE_EDITED — `POST /api/sessions/{sessionId}/events`

위에서 생성한 메시지(`MESSAGE_ID`) 의 본문을 수정한다.
payload 는 `{ "messageId": ..., "content": ... }`.

```bash
SESSION_ID=1
MESSAGE_ID=1

curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_EDITED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": ${MESSAGE_ID}, \"content\": \"수정된 내용\" }
  }"
```

검증 포인트:

- HTTP 200, 응답 `data.payload.messageId == MESSAGE_ID`, `data.payload.content == "수정된 내용"`
- DB 확인:
  - `events` 에 row 추가 (eventType=`MESSAGE_EDITED`)
  - `messages` 의 해당 row 가 **UPDATE** 됨 → `content == "수정된 내용"`, `edited_at IS NOT NULL`, `id`/`created_at` 불변

#### 음성 케이스

존재하지 않는 messageId → `404 MESSAGE_NOT_FOUND`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_EDITED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": 9999, \"content\": \"x\" }
  }"
```

타인이 작성한 메시지 수정 시도 → `403 FORBIDDEN_MESSAGE_OWNER`
(`MESSAGE_ID` 는 user1 이 작성한 메시지라고 가정):

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2" \
  -d "{
    \"eventType\": \"MESSAGE_EDITED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": ${MESSAGE_ID}, \"content\": \"x\" }
  }"
```

다른 세션의 messageId → `404 MESSAGE_NOT_FOUND`
(존재 노출 회피 차원에서 FORBIDDEN 이 아닌 NOT_FOUND 로 응답).

빈 content → `400 INVALID_EVENT_PAYLOAD`:

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_EDITED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": ${MESSAGE_ID}, \"content\": \"   \" }
  }"
```

이미 삭제된 메시지에 대한 EDIT → `409 MESSAGE_ALREADY_DELETED` (DELETE 검증 후 재시도하여 확인).

### MESSAGE_DELETED — `POST /api/sessions/{sessionId}/events`

메시지를 soft delete 한다. payload 는 `{ "messageId": ... }`.

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_DELETED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": ${MESSAGE_ID} }
  }"
```

검증 포인트:

- HTTP 200, 응답 `data.payload.messageId == MESSAGE_ID`
- DB 확인:
  - `events` 에 row 추가 (eventType=`MESSAGE_DELETED`)
  - `messages` 의 해당 row 는 **soft delete** → `deleted_at IS NOT NULL`, `content` 는 원본 그대로 (응답 마스킹은 조회 API 책임)

#### 멱등 — 동일 clientEventId 재시도

같은 `clientEventId` 로 재호출 시 응답이 첫 호출과 동일하고 `events` row 가 늘지 않는다 (MESSAGE_CREATED 멱등 검증과 동일 패턴).

#### 멱등 — 다른 clientEventId 로 재삭제

이미 삭제된 메시지에 **다른** `clientEventId` 로 다시 DELETE 요청.

```bash
curl -i -X POST "http://localhost:8080/api/sessions/${SESSION_ID}/events" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d "{
    \"eventType\": \"MESSAGE_DELETED\",
    \"clientEventId\": \"$(uuidgen)\",
    \"payload\": { \"messageId\": ${MESSAGE_ID} }
  }"
```

검증 포인트:

- HTTP 200 — 도메인 `softDelete()` 가 no-op 으로 처리
- DB 확인:
  - `events` 에는 row 가 **추가** 됨 (멱등 단위는 `client_event_id`)
  - `messages.deleted_at` 는 처음 삭제 시각에서 **변경되지 않음**

#### 음성 케이스

존재하지 않는 messageId → `404 MESSAGE_NOT_FOUND`.

타인 메시지 삭제 시도 → `403 FORBIDDEN_MESSAGE_OWNER`.

다른 세션의 messageId → `404 MESSAGE_NOT_FOUND`.

---

## message

### 세션 메시지 조회 — `GET /api/sessions/{sessionId}/messages`

세션 멤버가 해당 세션의 메시지를 `created_at` 오름차순으로 전체 조회한다.
페이징은 미도입 (1:1 채팅 + 과제 스펙 외).

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/messages" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": [
    {
      "id": 1,
      "sessionId": 1,
      "userId": 1,
      "content": "삭제된 메시지입니다.",
      "createdAt": "2026-04-29T...",
      "editedAt": "2026-04-29T...",
      "deletedAt": "2026-04-29T..."
    }
  ]
}
```

검증 포인트:

- 정렬: `createdAt` 오름차순
- **편집된 메시지**: `content` 는 최신 본문, `editedAt` 채워짐 (UPDATE-on-edit 정책)
- **삭제된 메시지**: `content` 는 placeholder `"삭제된 메시지입니다."` 로 마스킹, `deletedAt` 채워짐
  (도메인 entity 의 원문 content 는 보존되며, 마스킹은 응답 DTO 단계에서만 적용됨 — `SELECT content FROM messages` 로 원문 확인 가능)
- 양쪽 사용자 모두 동일한 응답을 받음 (creator/joiner 구분 없음)

#### 음성 케이스

존재하지 않는 세션 → `404 SESSION_NOT_FOUND`:

```bash
curl -i "http://localhost:8080/api/sessions/9999/messages" \
  -H "X-User-Id: 1"
```

세션 멤버가 아닌 사용자 → `403 FORBIDDEN_PARTICIPANT`:

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/messages" \
  -H "X-User-Id: 3"
```

#### 종료된 세션 조회

종료된 세션도 **조회는 허용** 된다 (히스토리 열람 목적). write 경로(events POST) 와 의도적으로 다른 정책.
세션 종료 API 도입 후, 세션을 종료시킨 뒤 위 조회 요청을 다시 실행하여 200 응답 + 동일한 메시지 목록이 반환되는지 확인.

---

## timeline

### 특정 시점 세션 상태 조회 — `GET /api/sessions/{sessionId}/timeline?at={ISO8601}`

events 를 fold 하여 시점 T 의 세션 상태(메시지 + 라이프사이클 + 도메인 멤버십)를 재구성한다.
**결정성**: 같은 (sessionId, T) 는 호출 시각과 무관하게 동일한 응답 — events 가 immutable + append-only 라 자동 보장.

도메인 멤버십 (`creatorLeftAt` / `joinerLeftAt`) 은 events 의 LEAVE row fold 결과 — connection presence (`/presence`, MockSessionManager) 와는 별개의 개념. presence 는 WebSocket 연결 휘발 상태, 멤버십은 LEAVE 도메인 행동의 영속 상태이다.

본 프로젝트의 JOIN 이벤트는 WebSocket 첫 connect 의미이며 방 입장과 무관 — 1:1 채팅의 방 입장은 세션 생성 자체이므로 timeline 멤버십 fold 는 LEAVE 만 본다.

```bash
SESSION_ID=1
AT="2026-04-30T12:00:00Z"

curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/timeline?at=${AT}" \
  -H "X-User-Id: 1"
```

기대 응답 (본문 예시):

```json
{
  "data": {
    "sessionId": 1,
    "at": "2026-04-30T12:00:00Z",
    "status": "SUSPENDED",
    "endedAt": null,
    "endedBy": null,
    "creatorLeftAt": null,
    "joinerLeftAt": null,
    "messages": [
      {
        "id": 1, "userId": 1,
        "content": "안녕하세요",
        "createdAt": "2026-04-30T11:00:00Z",
        "editedAt": "2026-04-30T11:30:00Z",
        "deletedAt": null
      },
      {
        "id": 2, "userId": 2,
        "content": "삭제된 메시지입니다.",
        "createdAt": "2026-04-30T11:15:00Z",
        "editedAt": null,
        "deletedAt": "2026-04-30T11:45:00Z"
      }
    ]
  }
}
```

검증 포인트:

- `at` 시점까지의 사실만 반영됨 (이후 발생 이벤트는 무시):
  - `at` 이전에 EDIT 됐으면 그 content / editedAt 반영
  - `at` 이전에 DELETE 됐으면 마스킹 + deletedAt 채워짐
  - `at` 이후의 CREATE/EDIT/DELETE 는 응답에 없음
- 라이프사이클:
  - `at` 이전에 SESSION_ENDED 발행됐으면 `status=ENDED`, `endedAt`, `endedBy` 채워짐
  - 아니면 `status=SUSPENDED` (초기값), endedAt/endedBy null
- 도메인 멤버십 (LEAVE fold 결과):
  - `at` 이전에 creator 가 `/end` 호출했으면 `creatorLeftAt` 채워짐, 아니면 null
  - `joinerLeftAt` 도 동일
  - 두 필드는 다른 시각이 될 수 있음 — 한쪽이 먼저 LEAVE 한 후 상대가 뒤늦게 LEAVE 한 경우 시점 t 에 따라 한쪽만 채워질 수 있음
- `at = NOW` 호출 시 현재 GET `/api/sessions/{id}/messages` 결과와 메시지 셋 일치
- **결정성 검증**: 동일 `at` 으로 시간 차를 두고 두 번 호출 → 응답 100% 동일 (`messages` 순서/필드 + leftAt 들까지)

#### 시나리오 — 시점 비교

권장 검증 절차:
1. 메시지 2건 생성 (T1, T2)
2. T_mid = T2 와 다음 작업 사이의 시각 기록
3. 메시지 1건 EDIT (T3 > T_mid)
4. timeline(T_mid) 호출 → EDIT 미반영(원본 content), editedAt=null
5. timeline(T3+ε) 호출 → EDIT 반영, editedAt 채워짐
6. timeline(T_mid) 다시 호출 → 4번과 완전히 동일 응답

#### 시나리오 — 멤버십 fold (LEAVE)

권장 검증 절차:
1. 메시지 1건 생성 (T1)
2. user1 이 `/end` 호출 (T2) — LEAVE + SESSION_ENDED
3. timeline(T1+ε) 호출 → `creatorLeftAt=null`, `joinerLeftAt=null`, `status=SUSPENDED` (LEAVE 이전 시점)
4. timeline(T2+ε) 호출 → `creatorLeftAt=T2`, `joinerLeftAt=null`, `status=ENDED`, `endedAt=T2`, `endedBy=1`
5. user2 가 뒤늦게 `/end` 호출 (T3 > T2)
6. timeline(T3+ε) 호출 → `creatorLeftAt=T2`, `joinerLeftAt=T3` (양쪽 다 채워짐), `endedAt=T2` (라이프사이클은 첫 전이 시각 그대로)

검증 포인트:

- T1 시점에는 두 leftAt 모두 null (events 의 LEAVE row 가 아직 없음)
- T2 시점에는 user1 의 leftAt 만 채워짐 — fold 가 user_id 별로 LEAVE 를 분리해 매핑함
- 두 번째 LEAVE 가 뒤늦게 들어와도 첫 LEAVE 의 시각이 보존됨 (folder 의 `putIfAbsent` 정책, 도메인 `Session.leave()` 와 일관)
- `endedAt` 은 첫 LEAVE 가 만든 SESSION_ENDED 의 createdAt — `joinerLeftAt` 시각보다 먼저인 게 정상

#### 음성 케이스

비멤버 호출 → `403 FORBIDDEN_PARTICIPANT`:

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/timeline?at=${AT}" \
  -H "X-User-Id: 3"
```

존재하지 않는 세션 → `404 SESSION_NOT_FOUND`.

`at` 이 세션 생성 이전 → `400 TIMELINE_AT_BEFORE_SESSION_CREATED`:

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/timeline?at=2000-01-01T00:00:00Z" \
  -H "X-User-Id: 1"
```

`at` 형식 오류 (ISO-8601 아님) → `400 INVALID_TIMELINE_AT`:

```bash
curl -i "http://localhost:8080/api/sessions/${SESSION_ID}/timeline?at=yesterday" \
  -H "X-User-Id: 1"
```

`at` 미래 시각 → `200` (현재 상태와 동등 응답, 결정성 속성 자연스럽게 만족).
