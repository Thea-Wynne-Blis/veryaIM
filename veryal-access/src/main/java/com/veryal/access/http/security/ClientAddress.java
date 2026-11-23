package com.veryal.access.http.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端地址解析。
 *
 * <p>站点统一由 nginx 反向代理（且 nginx 用 $remote_addr 覆盖 X-Forwarded-For），
 * 这里取代理链最左侧地址即可代表真实客户端。限流过滤器与注解切面共用这一份实现，
 * 避免两处各写一遍、日后只改一处。</p>
 */
final class ClientAddress {

    private ClientAddress() {
    }

    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
