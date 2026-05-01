# DB 설계

스키마 ddl 자체는 v1 마이그레이션 (`src/main/resources/db/migration/V1__init_schema.sql`) 을 권위로 합니다.

---

## 1. 테이블 개요

- users: 사용자
- sessions: 1:1 채팅 세션이며, creator_id / joiner_id 두 개의 컬럼으로 참가자를 표현합니다.

이벤트 테이블이 권위입니다. 메세지 테이블은 이벤트 테이블을 fold 하여 재생성 가능하도록 설계되어 있습니다.
정렬 키는 모두 created_at 으로, jpa 에 의해 insert 직전에 생성됩니다.

---

## 2. 인덱스 설계 근거

### 이벤트 테이블

- 유니크 제약조건 idx_events_client_event_id (client_event_id)
  - 동일한 이벤트의 중복 저장을 막기 위한 유니크 제약조건입니다.
  - 클라이언트가 입력한 client_event_id 값을 이용해서 중복 저장을 방지합니다.
  - where 조건에 `client_event_id is not null` 을 추가해서, 클라이언트가 트리거한 이벤트에 대해서만 유니크 제약조건이 작동하도록 하였습니다.
- 복합 인덱스 idx_events_session_created (session_id, created_at)
  - 조회 최적화를 위한 복합 인덱스입니다.
  - 이벤트는 세션 단위로 조회되므로 세션 id를 첫 번째 인덱스로 걸고, 정렬 기준으로 사용되는 created_at 을 포함시켰습니다.

### 세션 테이블

- 인덱스 idx_sessions_creator (creator_id)
  - 내가 만든 세션 목록 조회를 최적화하기 위한 인덱스입니다.
- 인덱스 index_sessions_joiner (joiner_id)
  - 내가 참여한 세션 목록 조회를 최적화하기 위한 인덱스입니다.
- partial 유니크 제약조건 uniq_active_session_per_pair (creator_id, joiner_id)
  - 동일한 두 사용자 사이에는 하나의 활성 세션만 존재하도록 강제하기 위한 partial 유니크 제약조건입니다.
  - least(creator_id, joiner_id), greatest(creator_id, joiner_id) 두 개의 조건을 이용해서, 서로가 creator, joiner 인 경우 모두를 검증합니다.
  - where status != ENDED 조건을 통해, 종료 상태의 세션은 제외합니다.

### 메세지 테이블

- 복합 인덱스 idx_messages_session_created (session_id, created_at)
  - 메세지를 세션 단위로 시간순 조회하기 위한 복합 인덱스입니다.
- soft delete 에 대한 partial index 미적용
  - 메세지 테이블은 deleted_at 을 이용하여 소프트 삭제를 구현하였습니다.
  - 삭제된 메세지도 조회되도록 ux 정의를 하였으므로, deleted_at 값이 있는 행을 제외하는 partial index 는 적용하지 않았습니다.

---

## 3. 정규화 선택 근거

### 메세지 테이블과 이벤트 테이블 이원화

이벤트 프로젝션 만으로 메세지 조회 기능을 모두 구현 가능합니다. 다만 이 경우 매 메세지 조회마다 이벤트를 fold 하는 비용이 발생합니다.

이 비용을 회피하기 위해 메세지 테이블을 별도로 두고, 이벤트 테이블과 같은 트랜잭션에서 갱신합니다.
현 시점 조회는 메세지 테이블만으로 가능하도록 하며, 특정 시점 기준의 뷰를 생성할 때에는 이벤트 테이블을 통해 replay 하도록 구현하였습니다. 

### 메세지 수정 및 삭제의 정규화

메세지 수정, 삭제시에는 메세지 테이블의 해당 레코드를 업데이트하는 방식을 사용하였습니다.
현재 상태의 메세지 조회는 '메세지 테이블'만으로 가능하도록 구현한다는 기준에 따른 결과입니다.

---

## 4. jsonb 선택 근거

events.payload 컬럼 타입으로 jsonb 를 채택했습니다.

채택 근거:

- 이벤트 타입마다 payload 형태가 다릅니다. 각 타입마다 별도의 테이블로 분리하면 정규화 필요성이 발생하여 비즈니스 확장 비용이 발생합니다. 
- postgres 의 jsonb 는 내부 필드에 대한 gin 인덱스가 가능합니다.
- 따라서 향후 payload 내부 필드 검색이 필요해지면 스키마 변경 없이 인덱스만 추가할 수 있습니다.

트레이드오프:

- jsonb 내부 필드는 외래키 대상으로 사용할 수 없습니다.
- 다만 본 프로젝트는 외래키를 강제하지 않으므로 영향이 없습니다.
- jpa 를 이용한 맵핑에서 불편이 존재합니다. 예를 들어, 현재 Map<String, Object> 를 이용하도록 구현되어 있으며, 각 이벤트에 따라 어떤 json 포맷을 가지는지 별도의 정리가 필요합니다.

---

## 5. 병목 / 한계

### 타임라인 fold 비용

세션 1개의 이벤트 수가 많아지면, fold 비용이 누적 events 수에 선형으로 증가합니다.
이에 따른 모니터링 관련 내용은 event-replay.md 문서에서 정리되어 있습니다.

---

## 6. 주요 쿼리 + 인덱스 + 병목

본 프로젝트의 쿼리 두 개를 선정해서 정리합니다.

### 6.1 시점 타임라인 fold (이벤트 테이블)

```sql
SELECT *
FROM events
WHERE session_id = ? AND created_at <= ?
ORDER BY created_at ASC;
```

- 호출 위치: `EventRepository.findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc`.
  - `SessionTimelineService.timeline` 의 fold 입력으로 사용됩니다.
- 인덱스: `idx_events_session_created (session_id, created_at)`
  - session_id 컬럼 인덱스를 우선 이용하고, created_at 컬럼이 created_at 정렬을 처리합니다.
  - where 와 order 가 같은 인덱스로 모두 커버됩니다.

병목:

- 세션 1개의 누적 events 수에 선형 비용입니다. 매우 길어진 세션에서 fold 비용이 응답 시간에 잡히기 시작합니다.

개선 방향:

- 스냅샷을 도입하여 fold 시작점을 단축하는 방법이 있습니다. 자세한 trade-off 와 도입 트리거는 event-replay.md 파일에 정리되어 있습니다.

### 6.2 사용자에게 보이는 세션 목록 (세션 테이블)

```sql
SELECT *
FROM sessions
WHERE (creator_id = ? AND creator_left_at IS NULL)
   OR (joiner_id  = ? AND joiner_left_at  IS NULL)
ORDER BY created_at DESC;
```

- 호출 위치: `SessionRepository.findVisibleByUserOrderByCreatedAtDesc`.
  - `SessionService.list` 가 사용합니다.
- 인덱스: `idx_sessions_creator (creator_id)` + `idx_sessions_joiner (joiner_id)`
  - or 의 양쪽 분기를 각각의 단일 인덱스가 커버합니다.
  - `left_at IS NULL` 필터는 인덱스 단계가 아니라 row 단계에서 적용됩니다.

병목:

- order by created_at desc 는 위 인덱스로 직접 커버되지 않습니다. 분리된 정렬 비용이 발생합니다.
- 사용자별 세션 수가 많아지면 정렬 비용 + 응답 페이로드가 누적됩니다.

개선 방향:

- created_at 커서를 활용한 페이지네이션을 도입할 수 있습니다.
- 카카오톡처럼 최근 채팅을 상단에 배치하려면 last_updated_at 컬럼을 추가하여 인덱스 목적으로 사용할 수 있습니다.

---
