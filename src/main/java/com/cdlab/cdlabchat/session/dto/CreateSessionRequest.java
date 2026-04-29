package com.cdlab.cdlabchat.session.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "joinerId 는 필수입니다.")
    private Long joinerId;
}
