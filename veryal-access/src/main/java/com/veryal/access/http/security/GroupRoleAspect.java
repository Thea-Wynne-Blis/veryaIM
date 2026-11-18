package com.veryal.access.http.security;

import com.veryal.application.group.GroupAccessGuard;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * {@link RequireGroupRole} 切面：解析群 ID，校验当前用户在群里的角色，不足直接抛 403。
 *
 * <p>排在限流切面之后、事务之前，被拒绝的请求不会占用数据库连接。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 45)
@RequiredArgsConstructor
public class GroupRoleAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final GroupAccessGuard groupAccessGuard;

    @Around("@annotation(requireGroupRole)")
    public Object around(ProceedingJoinPoint joinPoint, RequireGroupRole requireGroupRole)
            throws Throwable {
        AuthenticatedUser user = findUser(joinPoint.getArgs());
        if (user == null) {
            throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "authentication required");
        }
        groupAccessGuard.requireRole(resolveGroupId(joinPoint, requireGroupRole.groupId()),
                user.userId(), requireGroupRole.value());
        return joinPoint.proceed();
    }

    private AuthenticatedUser findUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuthenticatedUser user) {
                return user;
            }
        }
        return null;
    }

    /**
     * 按 SpEL 取群 ID。参数名未编入 class 时（缺少 -parameters）回退到第二个参数，
     * 群接口的签名统一是 {@code (当前用户, groupId, ...)}。
     */
    private String resolveGroupId(ProceedingJoinPoint joinPoint, String expression) {
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            if (names != null && i < names.length) {
                context.setVariable(names[i], args[i]);
            }
        }
        Object value;
        try {
            value = PARSER.parseExpression(expression).getValue(context);
        } catch (RuntimeException e) {
            value = null;
        }
        if (value == null && args.length > 1) {
            value = args[1];
        }
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("@RequireGroupRole cannot resolve groupId");
        }
        return value.toString();
    }
}
