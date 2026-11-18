package com.veryal.access.http.security;

import com.veryal.domain.group.GroupRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 群接口身份校验：进入控制器方法前确认当前用户在该群里的角色。
 *
 * <p>只做入口拦截，告诉调用方「你没有这个身份」；具体到目标成员能否被踢/被禁言，
 * 由服务层按角色矩阵再判断一次，避免绕过入口直接调服务。</p>
 *
 * <p>示例：</p>
 * <pre>
 * &#64;RequireGroupRole(GroupRole.OWNER)
 * &#64;PostMapping("/{groupId}/transfer")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireGroupRole {

    /**
     * 至少需要达到的角色。
     */
    GroupRole value();

    /**
     * 群 ID 的 SpEL 表达式，默认取名为 {@code groupId} 的路径参数。
     */
    String groupId() default "#groupId";
}
