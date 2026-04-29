-- ============================================================
-- users
-- ============================================================
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (name) VALUES ('user1');
INSERT INTO users (name) VALUES ('user2');

-- ============================================================
-- sessions
-- 1:1 채팅 세션. creator / joiner 두 컬럼으로 멤버십 표현.
-- ============================================================
CREATE TABLE sessions (
    id          BIGSERIAL   PRIMARY KEY,
    creator_id  BIGINT      NOT NULL REFERENCES users(id),
    joiner_id   BIGINT      NOT NULL REFERENCES users(id),
    status      VARCHAR(16) NOT NULL DEFAULT 'SUSPENDED',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at    TIMESTAMP   NULL,

    CONSTRAINT sessions_distinct_members CHECK (creator_id <> joiner_id),
    CONSTRAINT sessions_status_valid     CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED'))
);

CREATE INDEX idx_sessions_creator ON sessions(creator_id);
CREATE INDEX idx_sessions_joiner  ON sessions(joiner_id);

-- 1쌍 1세션 정책: 같은 두 사용자 사이의 진행 중 세션은 1개로 제한.
-- LEAST/GREATEST 로 (A,B) 와 (B,A) 를 동일 키로 정규화.
-- status='ENDED' 는 제약에서 제외 → 종료 후 동일 페어 재생성 가능.
CREATE UNIQUE INDEX uniq_active_session_per_pair
    ON sessions (LEAST(creator_id, joiner_id), GREATEST(creator_id, joiner_id))
    WHERE status <> 'ENDED';

-- ============================================================
-- events
-- append-only 권위 로그. 모든 상태 변화 보존.
-- ============================================================
CREATE TABLE events (
    id               BIGSERIAL    PRIMARY KEY,
    session_id       BIGINT       NOT NULL REFERENCES sessions(id),
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    event_type       VARCHAR(32)  NOT NULL,
    payload          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    client_event_id  UUID         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT events_type_valid CHECK (event_type IN (
        'MESSAGE_CREATED', 'MESSAGE_EDITED', 'MESSAGE_DELETED',
        'JOIN', 'LEAVE', 'DISCONNECT', 'RECONNECT', 'SESSION_ENDED'
    ))
);

-- client 발급 idempotency 키. server-emitted 이벤트는 NULL 다수 허용.
CREATE UNIQUE INDEX idx_events_client_event_id
    ON events(client_event_id)
    WHERE client_event_id IS NOT NULL;

-- 핫쿼리: 세션 내 시간순 조회 + 시점 복원 fold 쿼리 커버.
CREATE INDEX idx_events_session_created
    ON events(session_id, created_at);

-- ============================================================
-- messages
-- events 의 projection cache. 권위는 events.
-- ============================================================
CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT    NOT NULL REFERENCES sessions(id),
    user_id     BIGINT    NOT NULL REFERENCES users(id),
    content     TEXT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at   TIMESTAMP NULL,
    deleted_at  TIMESTAMP NULL
);

-- 핫쿼리: 세션 내 시간순 메시지 조회.
-- partial index (WHERE deleted_at IS NULL) 는 채택 안 함 — placeholder 패턴 쿼리는
-- deleted_at 필터를 두지 않으므로 partial index 가 매칭되지 않음.
CREATE INDEX idx_messages_session_created
    ON messages(session_id, created_at);
