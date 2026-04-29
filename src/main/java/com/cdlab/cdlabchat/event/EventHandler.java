package com.cdlab.cdlabchat.event;

import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.user.User;

import java.util.Map;
import java.util.UUID;

/**
 * event_type 별 처리 전략.
 * 각 구현체는 자신의 EventType 한 종을 담당하고, EventService 가 Map 디스패치한다.
 *
 * 책임:
 *  - payload 검증
 *  - events INSERT
 *  - 필요 시 projection (messages / sessions) 같은 트랜잭션 내 갱신
 */
public interface EventHandler {

    EventType supports();

    Event handle(Session session,
                 User currentUser,
                 Map<String, Object> payload,
                 UUID clientEventId);
}
