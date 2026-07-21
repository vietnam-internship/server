package com.fptis.intern.server.global.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Value("${travelx.dev.auth.cookie-same-site:Lax}")
    private String sameSite;

    public void addRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMillis) {
        addCookie(response, REFRESH_TOKEN_COOKIE_NAME, token, "/auth", maxAgeMillis);
    }

    public String getRefreshTokenFromRequest(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE_NAME);
    }

    public void clearTokenCookies(HttpServletResponse response) {
        expireCookie(response, REFRESH_TOKEN_COOKIE_NAME, "/auth");
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, long maxAgeMillis) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(!"local".equals(activeProfile));
        cookie.setPath(path);
        cookie.setMaxAge((int) (maxAgeMillis / 1000));
        cookie.setAttribute("SameSite", sameSite);
        response.addCookie(cookie);
    }

    private void expireCookie(HttpServletResponse response, String name, String path) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(!"local".equals(activeProfile));
        cookie.setPath(path);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
