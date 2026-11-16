package com.veryal.domain.push;

/**
 * Web Push 发送端口，由接入层实现（RFC 8291 加密 + VAPID 签名 + HTTP 投递）。
 */
public interface WebPushSender {

    /**
     * @return true 表示订阅已失效（HTTP 404/410），调用方应删除该订阅
     */
    boolean send(WebPushSubscription subscription, WebPushPayload payload);

    /**
     * VAPID 公钥（base64url），前端 {@code pushManager.subscribe} 的 applicationServerKey。
     */
    String vapidPublicKey();
}
