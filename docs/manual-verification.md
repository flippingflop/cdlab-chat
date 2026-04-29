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

| id | name |
|----|------|
| 1  | user1 |
| 2  | user2 |

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

- `data.messageId` 가 응답 최상위 필드로 채워짐
- `data.payload` 안에도 `messageId` 가 enrich 되어 있음 (server-enriched payload)
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
(creator=1, joiner=2 인 세션에 임의 user id 3 으로 호출. 단, 3번 사용자 시드를 추가했거나 별도 사용자 등록이 선행되어야 함):

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
