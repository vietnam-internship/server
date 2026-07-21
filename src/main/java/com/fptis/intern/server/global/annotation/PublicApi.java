package com.fptis.intern.server.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증 불필요 API임을 명시적으로 표시한다.
 * AuthInterceptor는 기본적으로 {@link RequireAuth}가 없으면 통과시키므로 없어도 동작은 같지만,
 * "의도적으로 공개했다"를 코드로 남기기 위해 auth 엔드포인트에는 붙인다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
