# Event Payload Spec

본 문서는 `events` 테이블의 `payload` (JSONB) 가 각 `EventType` 별로 어떤 구조를 가지는지 정의한다.

`events` 테이블이 권위(authority)이고 `messages` / `sessions.status` 는 그로부터 파생되는 프로젝션이라는 것이 본 설계의 전제다.

---

## 공통 규칙

- payload 는 JSONB 컬럼. 빈 페이로드는 `{}` 로 저장 (NOT NULL DEFAULT '{}').
- `client_event_id` 는 클라이언트 발행 이벤트에 대해서만 채워지며, 중복 방지 키로 동작한다.
- `messageId` 는 `messages.id` (프로젝션 PK) 를 참조한다. `events.id` 가 아니다.
- 클라이언트가 보낸 페이로드(=command) 와 DB 에 저장되는 페이로드(=event) 는 다를 수 있다. 서버가 처리 결과를 페이로드에 enrich 해 저장하므로, 본 문서의 표는 **저장 시점의 페이로드** 를 기준으로 한다. 클라이언트 발신 페이로드와 차이가 있는 경우 항목별로 명시한다.

---

## 이벤트 분류

| 발행 주체 | 이벤트 타입 | client_event_id |
|---|---|---|
| 클라이언트 | `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `JOIN`, `LEAVE` | 필수 (UUID) |
| 서버 | `DISCONNECT`, `RECONNECT`, `SESSION_ENDED` | NULL |

---

## 이벤트별 페이로드 스펙

각 이벤트 타입은 아래와 같은 형식의 표로 정리한다.

### MESSAGE_CREATED

세션 참여자가 새 메시지를 작성한 사실을 기록. `messages` 테이블에 새 row 가 INSERT 되며, 그 PK 가 페이로드에 enrich 된다.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `content`   | string | Y | 메시지 본문. 공백/빈 문자열 불가. |
| `messageId` | long   | Y | INSERT 된 `messages.id`. 서버가 채움. 클라이언트 발신 페이로드에는 없다. |

클라이언트 발신 페이로드는 `{ "content": "..." }` 만 포함한다.

---

## 상태 전이 다이어그램 (요약)

```
                  LEAVE / SESSION_ENDED
       ┌──────────────────────────────────┐
       │                                  ▼
   ┌───────┐  DISCONNECT  ┌──────────┐   ┌──────┐
   │ ACTIVE│ ───────────▶ │SUSPENDED │   │ ENDED│
   │       │ ◀─────────── │          │   │      │
   └───────┘   JOIN* /    └──────────┘   └──────┘
                RECONNECT*     ▲              ▲
              (양쪽 모두 연결)   │              │
                                │ [세션 생성]   │ LEAVE /
                                │              │ SESSION_ENDED
                              [생성]
```

(*) JOIN / RECONNECT 의 ACTIVE 전이는 "양쪽 모두 연결" 조건을 만족할 때만 일어난다.
세션 생성 직후 초기 상태는 `SUSPENDED`. ENDED 는 종착 상태(terminal).
