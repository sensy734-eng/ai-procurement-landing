package com.zufe.procurement.auth;

/**
 * 当前用户线程变量。请求结束由拦截器清除，避免线程复用串号。
 */
public final class AuthContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser getOrThrow() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前请求未携带登录上下文");
        }
        return user;
    }

    public static Long getUserIdOrNull() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
