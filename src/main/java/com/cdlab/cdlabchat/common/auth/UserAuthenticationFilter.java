package com.cdlab.cdlabchat.common.auth;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.user.User;
import com.cdlab.cdlabchat.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class UserAuthenticationFilter extends OncePerRequestFilter {

    public static final String CURRENT_USER_ATTR = "currentUser";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String HEALTH_PATH = "/api/health";

    private final UserService userService;
    private final HandlerExceptionResolver exceptionResolver;

    public UserAuthenticationFilter(
            UserService userService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.userService = userService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HEALTH_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long userId = extractUserId(request);
            User user = userService.getOrThrow(userId);
            request.setAttribute(CURRENT_USER_ATTR, user);
        } catch (BusinessException e) {
            exceptionResolver.resolveException(request, response, null, e);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    USER_ID_HEADER + " 헤더가 필요합니다.");
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    USER_ID_HEADER + " 헤더 형식이 올바르지 않습니다: " + header);
        }
    }
}
