package org.L2.common.context;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求链路追踪上下文。
 *
 * <p>HTTP 请求、Feign 调用和 MQ 消息统一使用 {@code X-Request-Id}，
 * Servlet 服务内通过 MDC 绑定到当前工作线程。</p>
 */
public final class RequestTraceContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private RequestTraceContext() {
    }

    public static String resolveOrCreate(String requestId) {
        if (requestId != null) {
            String normalized = requestId.trim();
            if (VALID_REQUEST_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static void bind(String requestId) {
        MDC.put(MDC_KEY, resolveOrCreate(requestId));
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrCreate() {
        return resolveOrCreate(current());
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
