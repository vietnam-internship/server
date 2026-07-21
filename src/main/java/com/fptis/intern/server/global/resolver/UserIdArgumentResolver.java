package com.fptis.intern.server.global.resolver;

import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.filter.UserIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UserId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object userId = request == null ? null : request.getAttribute(UserIdFilter.USER_ID_ATTRIBUTE_KEY);

        UserId annotation = parameter.getParameterAnnotation(UserId.class);
        if (annotation != null && annotation.required() && userId == null) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }

        return userId;
    }
}
