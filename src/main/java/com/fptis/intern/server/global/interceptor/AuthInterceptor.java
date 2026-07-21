package com.fptis.intern.server.global.interceptor;

import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.filter.UserIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @RequireAuth가 붙은 핸들러만 막는다 — 기본은 통과(opt-in). 새 엔드포인트를 추가할 때
 * 보호가 필요하면 반드시 @RequireAuth를 붙여야 한다는 뜻이므로, 컨트롤러 리뷰 시 체크 포인트다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (handlerMethod.hasMethodAnnotation(PublicApi.class)) {
            return true;
        }

        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        if (requireAuth == null) {
            return true;
        }

        Object userIdAttr = request.getAttribute(UserIdFilter.USER_ID_ATTRIBUTE_KEY);
        if (userIdAttr == null) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }

        if (requireAuth.roles().length > 0) {
            Long userId = (Long) userIdAttr;
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));

            boolean hasRole = Arrays.stream(requireAuth.roles())
                    .anyMatch(role -> user.getRole().name().equals(role));
            if (!hasRole) {
                throw new BusinessException(BusinessErrorCode.FORBIDDEN);
            }
        }

        return true;
    }
}
