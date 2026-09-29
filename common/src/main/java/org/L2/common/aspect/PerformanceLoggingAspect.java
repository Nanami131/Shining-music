package org.L2.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.context.RequestTraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Service 层性能切面。正常调用只记录慢调用，异常调用始终记录耗时。
 */
@Aspect
@Component
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PerformanceLoggingAspect {

    private static final long SLOW_CALL_THRESHOLD_MS = 300L;

    @Pointcut("execution(public * org.L2..application.service..*(..)) || "
            + "execution(public * org.L2..domain.service..*(..)) || "
            + "execution(public * org.L2.recommend.service..*(..))")
    public void serviceOperation() {
    }

    @Around("serviceOperation()")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long durationMs = elapsedMillis(startedAt);
            if (durationMs >= SLOW_CALL_THRESHOLD_MS) {
                log.warn("slow_service requestId={} method={} durationMs={} thresholdMs={}",
                        requestId(), joinPoint.getSignature().toShortString(), durationMs, SLOW_CALL_THRESHOLD_MS);
            }
            return result;
        } catch (Throwable throwable) {
            long durationMs = elapsedMillis(startedAt);
            log.error("service_error requestId={} method={} durationMs={} exception={}",
                    requestId(), joinPoint.getSignature().toShortString(), durationMs,
                    throwable.getClass().getSimpleName());
            throw throwable;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String requestId() {
        String requestId = RequestTraceContext.current();
        return requestId != null ? requestId : "-";
    }
}
