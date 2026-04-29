package com.cdlab.cdlabchat.session;

import com.cdlab.cdlabchat.common.auth.CurrentUser;
import com.cdlab.cdlabchat.common.response.ApiResponse;
import com.cdlab.cdlabchat.session.dto.CreateSessionRequest;
import com.cdlab.cdlabchat.session.dto.CreateSessionResponse;
import com.cdlab.cdlabchat.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ApiResponse<CreateSessionResponse> create(
            @CurrentUser User currentUser,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        Session session = sessionService.create(currentUser, request.getJoinerId());
        return ApiResponse.of(CreateSessionResponse.from(session));
    }
}
