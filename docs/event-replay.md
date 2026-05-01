# 이벤트 기반 상태 복원

본 문서는 과제 4.3 "이벤트 기반 상태 복원" 의 평가 항목을 정리합니다.

## 1. 전체 리플레이 vs 스냅샷+리플레이

### 채택 : 매 요청 전체 리플레이

현재 타임라인 조회 api는 매 요청마다 (sessionId, at) 에 해당하는 이벤트 부분집합을 처음부터 fold 합니다.
중간 스냅샷, projection 및 캐시는 두지 않습니다.

채택 근거:

- 본 과제는 1:1 채팅이고, 세션 1개의 누적 이벤트 수가 작습니다. fold 로 인해 큰 오버헤드가 발생할 시점은 멀리 있습니다.
- 이벤트 테이블 자체가 single source of truth 이고 append only, immutable 입니다.
- 스냅샷을 도입한다는 것은 "어떤 시점의 어떤 상태를 어떤 주기로 저장할지" 라는 별도 구성요소를 추가한다는 뜻입니다. 그 복잡도를 정당화 할 정도의 비용이 발생하기 전까지는 도입을 미루는 편이 운영상 부담이 작습니다.

### 스냅샷 도입 시의 trade-off

이벤트 누적량이 fold 비용을 무시할 수 없는 수준까지 올라오면 스냅샷이 필요해집니다.

장점:

- 타임라인 fold 시작점을 단축할 수 있습니다. (예: snapshot.at)
- 매우 긴 세션에서도 응답 시간이 누적 이벤트 수에 따라 선형으로 늘지 않습니다.

대가:

- 스냅샷 자체의 정합성 책임이 추가됩니다. "어느 시점을 캡처할지", "이벤트가 추가될 때 어떻게 갱신할지", "갱신이 실패하면 어떻게 회복할지" 라는 별도 운영 영역이 생깁니다.
- 저장 포맷, ttl, 무효화 정책 등이 결정 항목이 됩니다.
- 저장소가 추가됩니다. (별도 테이블 또는 외부 캐시)

도입 결정 트리거:

단일 세션의 누적 이벤트 수가 응답 시간에 의미 있게 영향을 주기 시작하는 시점에 도입을 검토합니다.
design-doc.md 문서에 언급된 "timeline fold 비용" 메트릭으로 운영 중 모니터링하여 도입 시점을 결정합니다.

---

## 2. 중복 이벤트 / 순서 뒤바뀜에 대한 복원 정합성

이벤트 테이블이 single source of truth 이므로, 중복 및 순서 이슈는 다음 두 단계에서 처리됩니다.

- 입력 단계: 이벤트 테이블에 중복 row 가 쌓이지 않습니다.
- fold 단계: 같은 의미의 사실이 같은 결과로 수렴합니다.

### 입력 단계: client_event_id 멱등 + db unique

요약하면 다음과 같습니다 (자세한 내용은 design-doc.md 참조).

- 클라이언트가 트리거한 이벤트 (MESSAGE_CREATED, MESSAGE_EDITED, MESSAGE_DELETED, LEAVE) 는 client_event_id 를 멱등 키로 이용합니다.
- 요청 시 선조회로 기존 row 를 찾고, 쓰레드 경합이 발생하면 db 의 partial unique index 가 한쪽을 막습니다.
- 서버의 로직으로 저장되는 이벤트 (SESSION_ENDED, JOIN, RECONNECT, DISCONNECT) 는 client_event_id 가 NULL 입니다.

이벤트 테이블에 중복 row 가 없다는 점이 보장되므로, fold 단계는 "같은 row 가 두 번 들어올 가능성" 은 신경쓰지 않아도 됩니다.

### fold 단계: created_at 정렬 + 첫 시각 보존

이벤트 테이블 조회 자체가 created_at asc 로 정렬됩니다.
따라서 요청의 도착 순서가 어떻든, fold 입력은 항상 created_at 시간순입니다.

다만 클라이언트가 다른 client_event_id 로 같은 의미의 요청을 두 번 보낸 경우는 별개 이벤트로 테이블에 저장됩니다.

### created_at 생성 지점에 대해서

이 앱은 현재 jpa 를 이용하고 있습니다.
따라서 created_at의 값이 생성되는 정확한 시점은 commit time 이 아니며, @CreationTimestamp 에 의해 insert 직전에 생성됩니다.

---

## 3. 복원 비용 / 성능

### 핫쿼리와 인덱스

타임라인 생성의 핫쿼리는 다음 한 개입니다.

`EventRepository.findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc`

이를 커버하는 인덱스는 v1 스키마에 정의된 idx_events_session_created (session_id, created_at) 입니다.
이 쿼리에 대해서는 추가 인덱스가 필요하지 않습니다.

### 비용 곡선

세션 1개의 타임라인 fold 비용은 다음에 선형입니다.

- db i/o: 인덱스 스캔으로 부분집합 row 수에 비례합니다.
- fold 실행: LinkedHashMap 형태로 저장되는 메세지 길이의 수에 비례합니다.

따라서 세션 1개의 누적 이벤트 수가 작은 초기 비즈니스 시기에는 매 요청 fold 가 충분히 가볍습니다.
fold 비용이 응답 소요시간에 의미 있게 잡히기 시작하는 시점이 스냅샷 도입 트리거가 됩니다.

---
