package com.zufe.procurement.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 动作级权限校验注解，值为权限点编码，如 m2:request:submit。
 * 可标在方法或类上，多个编码为全部满足。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String[] value();
}
