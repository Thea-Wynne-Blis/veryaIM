package com.veryal.access.http.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 百度翻译通用 API 的服务端代理。
 *
 * <p>密钥只留在服务端；客户端只提交原文与目标语言。百度标准版 QPS 为 1，
 * 这里用进程内 LRU 缓存 + 最小调用间隔兜住连续点按。</p>
 */
@Slf4j
@Component
public class BaiduTranslator {

    /** 百度翻译支持的目标语言代码（帮助文档「语言列表」）。 */
    private static final Set<String> SUPPORTED = Set.of(
            "zh", "en", "yue", "wyw", "jp", "kor", "fra", "spa", "th", "ara", "ru", "pt",
            "de", "it", "el", "nl", "pl", "bul", "est", "dan", "fin", "cs", "rom", "slo",
            "swe", "hu", "cht", "vie");

    private static final int MAX_CACHE = 400;
    private static final long MIN_INTERVAL_MS = 1100L;
    private static final int MAX_Q_BYTES = 5500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final String appId;
    private final String secret;
    private final String endpoint;

    private final Map<String, Result> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    private long lastCallAt;

    public BaiduTranslator(@Value("${veryal.translate.app-id:}") String appId,
                           @Value("${veryal.translate.secret:}") String secret,
                           @Value("${veryal.translate.endpoint:https://fanyi-api.baidu.com/api/trans/vip/translate}")
                           String endpoint) {
        this.appId = appId == null ? "" : appId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.endpoint = endpoint == null || endpoint.isBlank()
                ? "https://fanyi-api.baidu.com/api/trans/vip/translate" : endpoint.trim();
    }

    /**
     * @param text 原文
     * @param to   百度语言代码，缺省英语
     */
    public Result translate(String text, String to) {
        String target = to == null || to.isBlank()
                ? "en" : to.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "unsupported target language: " + to);
        }
        String q = text == null ? "" : text.strip();
        if (q.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "text must not be blank");
        }
        if (q.getBytes(StandardCharsets.UTF_8).length > MAX_Q_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "text is too long to translate");
        }

        String cacheKey = target + '\u0000' + q;
        Result cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (appId.isEmpty() || secret.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFIGURATION_ERROR, "翻译服务未配置");
        }

        String salt = Long.toString(System.currentTimeMillis());
        String sign = md5(appId + q + salt + secret);
        String body = "q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&from=auto&to=" + target
                + "&appid=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "&salt=" + salt
                + "&sign=" + sign;

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        awaitSlot();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "翻译服务请求被中断", e);
        } catch (Exception e) {
            log.warn("baidu translate request failed: {}", e.toString());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "翻译服务暂时不可用", e);
        }

        if (response.statusCode() != 200) {
            log.warn("baidu translate http status={}", response.statusCode());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "翻译服务暂时不可用");
        }

        JsonNode root;
        try {
            root = JsonUtils.mapper().readTree(response.body());
        } catch (Exception e) {
            log.warn("baidu translate returned unparsable body");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "翻译服务返回异常");
        }

        String errorCode = root.path("error_code").asText("");
        if (!errorCode.isEmpty() && !"52000".equals(errorCode)) {
            log.warn("baidu translate error code={} msg={}", errorCode,
                    root.path("error_msg").asText(""));
            if ("54003".equals(errorCode) || "54004".equals(errorCode)) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "翻译请求过于频繁，请稍后再试");
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "翻译失败：" + root.path("error_msg").asText("服务暂不可用"));
        }

        StringBuilder translated = new StringBuilder();
        for (JsonNode item : root.path("trans_result")) {
            if (translated.length() > 0) {
                translated.append('\n');
            }
            translated.append(item.path("dst").asText(""));
        }
        String resultText = translated.toString();
        if (resultText.isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "翻译服务没有返回结果");
        }

        Result result = new Result(root.path("from").asText("auto"), target, resultText);
        cache.put(cacheKey, result);
        return result;
    }

    /** 百度标准版 QPS=1：两次真实调用之间至少留 1.1 秒（不持锁睡眠，避免钉住虚拟线程载体）。 */
    private void awaitSlot() {
        long wait;
        synchronized (this) {
            long now = System.currentTimeMillis();
            wait = MIN_INTERVAL_MS - (now - lastCallAt);
            lastCallAt = now + Math.max(0, wait);
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    public record Result(String from, String to, String text) {
    }
}
