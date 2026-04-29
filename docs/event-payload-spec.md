# Event Payload Spec

본 문서는 `events` 테이블의 `payload` (JSONB) 가 각 `EventType` 별로 어떤 구조를 가지는지 정의한다.

`events` 테이블이 권위(authority)이고 `messages` / `sessions.status` 는 그로부터 파생되는 프로젝션이라는 것이 본 설계의 전제다.

---

## 공통 규칙

- payload 는 JSONB 컬럼. 빈 페이로드는 `{}` 로 저장 (NOT NULL DEFAULT '{}').
- `client_event_id` 는 클라이언트 발행 이벤트에 대해서만 채워지며, 중복 방지 키로 동작한다.
- `messageId` 는 `messages.id` (프로젝션 PK) 를 참조한다. `events.id` 가 아니다.

---

## 이벤트 분류

| 발행 주체 | 이벤트 타입 | client_event_id |
|---|---|---|
| 클라이언트 | `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `JOIN`, `LEAVE` | 필수 (UUID) |
| 서버 | `DISCONNECT`, `RECONNECT`, `SESSION_ENDED` | NULL |

---

## 이벤트별 페이로드 스펙

각 이벤트 타입은 아래와 같은 형식의 표로 정리한다.

### EXAMPLE_EVENT (예시 — 실제 이벤트 추가 시 본 항목 대체)

해당 이벤트가 어떤 상황에서 발행되며, 무엇을 표현하는지 한두 줄.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `exampleField` | string | Y | 필드의 의미 |

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
