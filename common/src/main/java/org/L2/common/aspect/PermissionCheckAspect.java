package org.L2.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.annotation.PermissionCheck;
import org.L2.common.context.UserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 权限校验切面：验证当前已认证用户（来自网关 JWT）是否有权操作目标资源。
 * <p>
 * 使用方式：在 Service 方法上标注 {@code @PermissionCheck(fieldName = "userId")}，
 * 切面会从方法的第一个参数中提取 fieldName 对应的字段值，与 UserContext 中的
 * 已认证 userId 比较。不一致则抛出异常。
 */
@Aspect
@Component
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionCheckAspect {

    @Pointcut("@annotation(org.L2.common.annotation.PermissionCheck)")
    public void permissionCheckPointCut() {}

    @Before("permissionCheckPointCut() && @annotation(check)")
    public void checkPermission(JoinPoint joinPoint, PermissionCheck check) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            log.warn("权限校验失败：UserContext 中无用户身份，可能是未经网关的内部调用");
            throw new RuntimeException("用户未登录或身份信息缺失");
        }

        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        Object target = args[0];
        if (target == null) {
            return;
        }

        Long requestUserId = extractUserId(target, check.fieldName());
        if (requestUserId == null) {
            return;
        }

        if (!currentUserId.equals(requestUserId)) {
            log.warn("权限校验失败：当前用户 {} 尝试操作用户 {} 的资源", currentUserId, requestUserId);
            throw new RuntimeException("无权操作其他用户的数据");
        }

        log.debug("权限校验通过：userId={}", currentUserId);
    }

    private Long extractUserId(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                log.warn("目标对象 {} 中未找到字段: {}", target.getClass().getSimpleName(), fieldName);
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(target);
            if (value == null) {
                return null;
            }
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Integer) {
                return ((Integer) value).longValue();
            }
            if (value instanceof String) {
                return Long.parseLong((String) value);
            }
            log.warn("字段 {} 的类型 {} 不支持提取 userId", fieldName, value.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            log.error("提取 userId 字段失败: {}", e.getMessage());
            return null;
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
