package com.cdlab.cdlabchat.message;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.message.dto.MessageResponse;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionRepository;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public List<MessageResponse> findBySession(Long sessionId, User currentUser) {
        // 1) 세션 조회 / 멤버 검증 — 종료된 세션도 조회는 허용 (히스토리 열람)
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isMember(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PARTICIPANT);
        }

        // 2) messages 프로젝션 단독 조회 — UPDATE-on-edit 정책으로 content 는 항상 최신
        //    삭제된 메시지는 응답 DTO 단계에서 placeholder 로 마스킹 (위치/순서 보존)
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
