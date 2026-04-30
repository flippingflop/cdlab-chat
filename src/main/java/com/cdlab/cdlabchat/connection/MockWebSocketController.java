package com.cdlab.cdlabchat.connection;

import com.cdlab.cdlabchat.common.auth.CurrentUser;
import com.cdlab.cdlabchat.common.response.ApiResponse;
import com.cdlab.cdlabchat.connection.dto.ConnectResponse;
import com.cdlab.cdlabchat.connection.dto.DisconnectResponse;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채점자가 WebSocket open/close 를 REST 로 시뮬레이션할 수 있도록 노출되는 트리거 컨트롤러.
 *
 * 의도 — 운영 환경에서는 WebSocket 핸들러가 connect/disconnect 시 JOIN/RECONNECT/DISCONNECT
 * 이벤트를 자동 발행하고 presence 를 관리한다. 본 프로젝트는 WebSocket 을 채택하지 않았으므로
 * 동일 라이프사이클을 REST 트리거로 시뮬레이션한다 (design-decision.md "웹소켓 미구현" 참조).
 *
 * 별도 컨트롤러로 분리한 이유 —
 *  1) 클래스명에 "Mock" 을 박아 운영 시 제거/비활성화 대상임을 코드 단에서 표명.
 *  2) 도메인 라이프사이클(SessionController) 과 connection 라이프사이클 시뮬레이션의 책임 분리.
 *  3) 추후 운영 안전장치(@Profile("!prod") 등) 적용 단위로 자연스러움.
 *
 * LEAVE 는 본 컨트롤러가 아닌 SessionController#end 에 둔다 — LEAVE 는 "사용자가 명시적으로
 * 나가기를 누른" 도메인 의도 행동이며, 운영 환경에서도 그대로 살아남을 endpoint 이기 때문.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class MockWebSocketController {

    private final ConnectionService connectionService;

    @PostMapping("/{sessionId}/connect")
    public ApiResponse<ConnectResponse> connect(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.of(connectionService.connect(sessionId, currentUser));
    }

    @PostMapping("/{sessionId}/disconnect")
    public ApiResponse<DisconnectResponse> disconnect(
            @CurrentUser User currentUser,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.of(connectionService.disconnect(sessionId, currentUser));
    }
}
