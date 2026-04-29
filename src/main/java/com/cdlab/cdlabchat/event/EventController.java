package com.cdlab.cdlabchat.event;

import com.cdlab.cdlabchat.common.auth.CurrentUser;
import com.cdlab.cdlabchat.common.response.ApiResponse;
import com.cdlab.cdlabchat.event.dto.CollectEventRequest;
import com.cdlab.cdlabchat.event.dto.CollectEventResponse;
import com.cdlab.cdlabchat.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions/{sessionId}/events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ApiResponse<CollectEventResponse> collect(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody CollectEventRequest request
    ) {
        return ApiResponse.of(eventService.collect(sessionId, currentUser, request));
    }
}
