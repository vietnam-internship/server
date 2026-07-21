package com.fptis.intern.server.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증(및 선택적으로 특정 role)이 필요한 API에 붙인다.
 * 붙이지 않으면 AuthInterceptor는 통과시킨다 — 보호는 기본값이 아니라 옵트인이다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {

    /**
     * 필요한 역할. 비어 있으면 인증 여부만 확인한다.
     */
    String[] roles() default {};
}
