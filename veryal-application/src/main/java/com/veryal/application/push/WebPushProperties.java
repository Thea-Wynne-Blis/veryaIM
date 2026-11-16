package com.veryal.application.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Push（浏览器推送）配置。
 *
 * <p>默认关闭。开启后如果未配置 VAPID 密钥，会在 {@code key-file} 自动生成并持久化，
 * 保证重启后订阅仍然有效。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "veryal.push.web-push")
public class WebPushProperties {

    /** 总开关。 */
    private boolean enabled = false;

    /** VAPID subject，必须是 mailto: 或 https: 形式。 */
    private String subject = "mailto:admin@example.com";

    /** VAPID 公钥（base64url，65 字节未压缩点）。留空则从 key-file 读取或自动生成。 */
    private String publicKey = "";

    /** VAPID 私钥（base64url，32 字节标量）。留空则从 key-file 读取或自动生成。 */
    private String privateKey = "";

    /** 自动生成密钥的持久化文件。 */
    private String keyFile = "./data/vapid-keys.json";

    /** 消息保留时长（秒），Web Push 协议要求。 */
    private long ttlSeconds = 86400;

    /** 单用户最大订阅数，避免同一浏览器重复订阅堆积。 */
    private int maxSubscriptionsPerUser = 10;
}
