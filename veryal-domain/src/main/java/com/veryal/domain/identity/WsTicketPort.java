package com.veryal.domain.identity;

/**
 * WebSocket 一次性连接票据端口。
 *
 * <p>浏览器 WebSocket API 不能给握手设置 {@code Authorization} 头，
 * 把长期 Token 放 query 会进入 Nginx / 网关访问日志。因此客户端先用
 * Bearer Token 走 HTTP 换一张短时效、只能用一次的 ticket，再用 ticket 建连。</p>
 */
public interface WsTicketPort {

    /** 签发一张一次性票据。 */
    String issue(TicketPayload payload);

    /** 消费票据：过期、伪造、重复使用都返回 {@code null}。 */
    TicketPayload consume(String ticket);

    /** 票据有效期（秒），用于返回给客户端。 */
    long ttlSeconds();

    /** 票据绑定的身份：与登录态保持一致，包含 tokenId 以便登出/踢人语义不退化。 */
    record TicketPayload(long userId, String deviceId, String tokenId) {
    }
}
