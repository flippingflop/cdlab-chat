package com.cdlab.cdlabchat.session;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.user.User;
import com.cdlab.cdlabchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public Session create(User creator, Long joinerId) {
        // 1) 자기 자신과의 세션 차단 — DB CHECK 와 이중 방어
        if (creator.getId().equals(joinerId)) {
            throw new BusinessException(ErrorCode.SELF_SESSION_NOT_ALLOWED);
        }

        // 2) joiner 가 실제 존재하는 사용자인지 확인
        if (!userRepository.existsById(joinerId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3) 1쌍 1세션 사전 체크 — 같은 두 사용자(순서 무관) 사이의 진행 중 세션이 있으면 차단
        sessionRepository.findActiveBetween(creator.getId(), joinerId)
                .ifPresent(s -> { throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS); });

        // 4) 신규 세션 저장
        //    DB partial unique (LEAST/GREATEST) 가 동시 요청 race 의 최종 보루.
        //    사전 체크를 통과한 두 요청이 동시에 들어와도 DB 에서 한쪽은 실패한다.
        try {
            return sessionRepository.save(new Session(creator.getId(), joinerId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS);
        }
    }
}
