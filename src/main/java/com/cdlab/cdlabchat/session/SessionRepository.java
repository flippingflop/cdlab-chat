package com.cdlab.cdlabchat.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    // 사용자에게 보이는 세션 목록.
    // 멤버이면서 자기 쪽 left_at IS NULL 인 세션만 노출.
    // 정렬: created_at DESC (신규 세션 우선). ENDED 세션도 left_at 이 NULL 이면 히스토리로 노출.
    @Query("""
            SELECT s FROM Session s
            WHERE (s.creatorId = :userId AND s.creatorLeftAt IS NULL)
               OR (s.joinerId  = :userId AND s.joinerLeftAt  IS NULL)
            ORDER BY s.createdAt DESC
            """)
    List<Session> findVisibleByUserOrderByCreatedAtDesc(@Param("userId") Long userId);
}
