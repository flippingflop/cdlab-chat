package com.cdlab.cdlabchat.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    // 같은 두 사용자(순서 무관) 사이에 진행 중(ENDED 아님) 세션 조회.
    @Query("""
            SELECT s FROM Session s
            WHERE s.status <> com.cdlab.cdlabchat.session.SessionStatus.ENDED
              AND ((s.creatorId = :a AND s.joinerId = :b)
                OR (s.creatorId = :b AND s.joinerId = :a))
            """)
    Optional<Session> findActiveBetween(@Param("a") Long a, @Param("b") Long b);
}
