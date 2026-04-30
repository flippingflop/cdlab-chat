package com.cdlab.cdlabchat.session.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EndSessionRequest {

    /**
     * 클라이언트 발급 idempotency 키. /end 도 client-emitted 액션이므로 일관되게 요구.
     */
    @NotNull(message = "clientEventId 는 필수입니다.")
    private UUID clientEventId;
}
