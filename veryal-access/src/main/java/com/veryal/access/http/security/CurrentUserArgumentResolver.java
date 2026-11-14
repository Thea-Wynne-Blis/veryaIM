package com.veryal.access.http.security;

import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.exception.AuthenticationException;
import com.veryal.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 将过滤器写入的当前用户注入 Controller 参数。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        AuthenticatedUser user = request == null ? null
                : (AuthenticatedUser) request.getAttribute(AuthenticationFilter.USER_ATTRIBUTE);
        if (user == null) {
            throw new AuthenticationException(ErrorCode.UNAUTHORIZED, "authentication required");
        }
        return user;
    }
}
