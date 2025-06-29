package org.L2.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.OperationType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Date;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    // 定义切点：匹配所有 service 包下的方法，并且带有 @AutoFill 注解
    @Pointcut("execution(* org.L2.*.domain.service..*.*(..)) && @annotation(org.L2.common.annotation.AutoFill)")
    public void autoFillPointCut() {}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始自动填充时间字段");
        // 获取方法签名和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();

        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        // 约定第一个参数为DTO对象
        Object dto = args[0];
        if (dto == null) {
            return;
        }

        try {
            fillTimeFields(dto, operationType);
        } catch (Exception e) {
            log.error("自动填充时间字段失败: {}", e.getMessage());
        }
    }

    /**
     * 使用反射填充时间字段
     */
    private void fillTimeFields(Object dto, OperationType operationType) throws Exception {
        Class<?> clazz = dto.getClass();
        Field[] fields = clazz.getDeclaredFields(); //这个方法能获得包括私有字段的所有字段

        LocalDateTime now = LocalDateTime.now();
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();

            if ("createdAt".equals(fieldName) && operationType == OperationType.INSERT) {
                field.set(dto, now);
            } else if ("updatedAt".equals(fieldName)) {
                field.set(dto, now); // INSERT 和 UPDATE 都会更新 updateTime
            }
        }
    }
}
