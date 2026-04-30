package com.cdlab.cdlabchat.message;

import com.cdlab.cdlabchat.common.auth.CurrentUser;
import com.cdlab.cdlabchat.common.response.ApiResponse;
import com.cdlab.cdlabchat.message.dto.MessageResponse;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions/{sessionId}/messages")
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<List<MessageResponse>> list(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.of(messageService.findBySession(sessionId, currentUser));
    }
}
