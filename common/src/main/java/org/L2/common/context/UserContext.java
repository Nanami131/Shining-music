package org.L2.common.context;

/**
 * 基于 ThreadLocal 的用户上下文，持有当前请求的已认证用户 ID。
 * 由 {@link UserContextInterceptor} 自动管理生命周期。
 */
public class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
