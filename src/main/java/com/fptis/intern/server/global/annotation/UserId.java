package com.fptis.intern.server.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드의 Long 파라미터에 붙이면 UserIdArgumentResolver가
 * 요청 attribute에 UserIdFilter가 심어둔 userId를 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserId {
    boolean required() default true;
}
