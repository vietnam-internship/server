package com.fptis.intern.server.global.filter;

import com.fptis.intern.server.global.util.JwtProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더에서 access token을 뽑아 request attribute에 심어두기만 한다 —
 * 여기서 막지 않는다. 실제 인가는 AuthInterceptor(@RequireAuth)가 담당한다(opt-in 보호).
 * 프론트(client 레포)가 accessToken을 응답 바디로 받아 localStorage에 저장하고 Bearer 헤더로
 * 보내는 방식을 쓰므로 access token은 쿠키가 아니라 헤더가 유일한 채널이다 — refresh token만
 * 여전히 CookieUtil을 통한 httpOnly 쿠키로 오간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserIdFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE_KEY = "userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String accessToken = extractBearerToken(request);

        if (accessToken != null) {
            try {
                if (jwtProvider.validateToken(accessToken)) {
                    Long userId = jwtProvider.getUserIdFromToken(accessToken);
                    request.setAttribute(USER_ID_ATTRIBUTE_KEY, userId);
                    log.debug("[UserIdFilter] authenticated userId={}", userId);
                }
            } catch (ExpiredJwtException e) {
                log.debug("[UserIdFilter] access token expired - client should refresh");
            } catch (JwtException e) {
                log.warn("[UserIdFilter] invalid access token: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
