package org.L2.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionCheck {
    /**
     * 指定方法第一个参数中的用户 ID 字段名。
     * 切面会将该字段值与 UserContext 中已认证的 userId 比较。
     */
    String fieldName() default "userId";
}