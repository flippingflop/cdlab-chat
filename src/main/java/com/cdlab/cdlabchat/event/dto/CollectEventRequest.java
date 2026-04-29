package com.cdlab.cdlabchat.event.dto;

import com.cdlab.cdlabchat.event.EventType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CollectEventRequest {

    @NotNull(message = "eventType 은 필수입니다.")
    private EventType eventType;

    /**
     * event_type 별로 구조가 다른 가변 데이터.
     * 핸들러가 자체 검증한다.
     */
    private Map<String, Object> payload;

    /**
     * 클라이언트 발급 idempotency 키. 본 API 는 client-emitted 이벤트만 받으므로 필수.
     */
    @NotNull(message = "clientEventId 는 필수입니다.")
    private UUID clientEventId;
}
