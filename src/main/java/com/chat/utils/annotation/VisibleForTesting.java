package com.chat.utils.annotation;

import java.lang.annotation.*;

/**
 * 테스트 전용으로 사용되는 메서드, 필드, 클래스에 부여하는 어노테이션입니다.
 * 이 어노테이션은 컴파일 또는 런타임 동작에는 영향을 주지 않으며,
 * 코드의 가독성과 유지보수성을 높이는 문서화 목적입니다.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface VisibleForTesting {
}
