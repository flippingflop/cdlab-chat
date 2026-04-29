package com.cdlab.cdlabchat.session;

public enum SessionStatus {
    ACTIVE, // 두 사람 모두가 연결된 상태
    SUSPENDED, // 둘 중 한 사람 이상의 웹소켓 연결 불안정으로 인해 일시적으로 연결이 끊어진 상태
    ENDED // 둘 중 한 사람 이상이 명시적으로 세션에서 퇴장한 상태
}
