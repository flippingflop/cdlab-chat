package com.cdlab.cdlabchat.session;

import com.cdlab.cdlabchat.common.auth.CurrentUser;
import com.cdlab.cdlabchat.common.response.ApiResponse;
import com.cdlab.cdlabchat.session.dto.CreateSessionRequest;
import com.cdlab.cdlabchat.session.dto.CreateSessionResponse;
import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.session.dto.EndSessionRequest;
import com.cdlab.cdlabchat.session.dto.EndSessionResponse;
import com.cdlab.cdlabchat.session.dto.SessionListResponse;
import com.cdlab.cdlabchat.session.dto.SessionTimelineResponse;
import com.cdlab.cdlabchat.session.timeline.SessionTimelineService;
import com.cdlab.cdlabchat.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionTimelineService sessionTimelineService;

    @PostMapping
    public ApiResponse<CreateSessionResponse> create(
            @CurrentUser User currentUser,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        Session session = sessionService.create(currentUser, request.getJoinerId());
        return ApiResponse.of(CreateSessionResponse.from(session));
    }

    @GetMapping
    public ApiResponse<List<SessionListResponse>> list(
            @CurrentUser User currentUser
    ) {
        return ApiResponse.of(sessionService.list(currentUser));
    }

    @PostMapping("/{sessionId}/end")
    public ApiResponse<EndSessionResponse> end(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody EndSessionRequest request
    ) {
        return ApiResponse.of(sessionService.end(sessionId, currentUser, request.getClientEventId()));
    }

    @GetMapping("/{sessionId}/timeline")
    public ApiResponse<SessionTimelineResponse> timeline(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId,
            @RequestParam("at") String atRaw
    ) {
        // Spring 의 기본 Instant 바인딩이 없어 명시적으로 파싱 (포맷 오류는 명확한 에러로 변환)
        Instant at;
        try {
            at = Instant.parse(atRaw);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_TIMELINE_AT);
        }
        return ApiResponse.of(sessionTimelineService.timeline(sessionId, currentUser, at));
    }
}
