package com.veryal.access.http.sticker;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 网络表情包来源（斗图啦）。
 *
 * <p>站点本身是开放可抓取的：搜索结果页把每张表情放在
 * {@code <img class="image_dtb" src="https://img.doutupk.com/...">} 里。
 * 这里只解析这一种结构，命中不了就返回空列表，不做兜底猜测。</p>
 *
 * <p>安全边界：
 * <ul>
 *   <li>只允许 https 且主机在 {@link #ALLOWED_HOSTS} 白名单内，杜绝把接口变成 SSRF 跳板；</li>
 *   <li>不跟随重定向，下载体积硬上限 8MB，超限即中断；</li>
 *   <li>只接受 {@code image/*}，SVG 一律拒绝（可执行脚本）。</li>
 * </ul>
 * 搜索结果做 10 分钟进程内缓存，避免用户连续翻页时反复打对方站点。</p>
 */
@Slf4j
@Component
public class StickerSource {

    private static final Set<String> ALLOWED_HOSTS = Set.of("img.doutupk.com", "img.doutula.com");
    private static final int MAX_RESULTS = 48;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_CACHE = 200;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    /**
     * 结果项选择器：正常情况是 {@code img.image_dtb}；
     * 站点换版后类名变化时退一步按列表结构找图，避免整个功能直接失联。
     */
    private static final String SELECTOR = "img.image_dtb";
    private static final String FALLBACK_SELECTOR = "a.col-xs-6 img, .pic-content img, img.img-responsive";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String searchEndpoint;

    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    public StickerSource(
            @Value("${veryal.sticker.search-endpoint:https://www.doutula.com/search}")
            String searchEndpoint) {
        this.searchEndpoint = searchEndpoint == null || searchEndpoint.isBlank()
                ? "https://www.doutula.com/search" : searchEndpoint.trim();
    }

    /** 按关键词搜索，page 从 1 开始。 */
    public List<String> search(String keyword, int page) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "keyword is required");
        }
        if (kw.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "keyword is too long");
        }
        int safePage = Math.max(1, Math.min(page, 20));
        String cacheKey = kw + '#' + safePage;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.at() < CACHE_TTL_MS) {
            return cached.items();
        }
        List<String> items = fetch(kw, safePage);
        cache.put(cacheKey, new CacheEntry(items, System.currentTimeMillis()));
        return items;
    }

    /** 下载一张表情图，调用方负责落库。 */
    public Download download(String rawUrl) {
        URI uri = parseAllowed(rawUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (compatible; veryal/1.0)")
                .header("Accept", "image/*")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "sticker source returned " + response.statusCode());
            }
            String contentType = response.headers().firstValue("content-type")
                    .orElse("").toLowerCase(Locale.ROOT);
            contentType = contentType.split(";", 2)[0].trim();
            if (!contentType.startsWith("image/") || contentType.contains("svg")) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "unsupported sticker content type");
            }
            long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declared > MAX_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "sticker is too large");
            }
            byte[] content;
            try (InputStream input = response.body()) {
                content = input.readNBytes((int) MAX_BYTES + 1);
            }
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sticker is empty");
            }
            if (content.length > MAX_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "sticker is too large");
            }
            return new Download(content, contentType, fileNameOf(uri, contentType));
        } catch (IOException e) {
            log.warn("sticker download failed: url={}", rawUrl, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker download failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker download interrupted");
        }
    }

    private List<String> fetch(String keyword, int page) {
        String url = searchEndpoint + "?keyword="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&page=" + page;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (compatible; veryal/1.0)")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "sticker source returned " + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException e) {
            log.warn("sticker search failed: keyword={}", keyword, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker search failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker search interrupted");
        }
    }

    /** 从结果页里挑出表情缩略图；去重后最多 {@link #MAX_RESULTS} 张。 */
    static List<String> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document document = Jsoup.parse(html);
        Elements images = document.select(SELECTOR);
        if (images.isEmpty()) {
            images = document.select(FALLBACK_SELECTOR);
        }
        List<String> items = new ArrayList<>();
        for (Element image : images) {
            if (items.size() >= MAX_RESULTS) {
                break;
            }
            // 站点对缩略图做了懒加载：真实地址常在 data-original 上
            String src = firstHttpsUrl(
                    image.attr("data-original"), image.attr("data-src"), image.attr("src"));
            if (src != null && !items.contains(src)) {
                items.add(src);
            }
        }
        return List.copyOf(items);
    }

    private static String firstHttpsUrl(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String value = candidate.trim();
            if (value.startsWith("//")) {
                value = "https:" + value;
            }
            if (value.startsWith("https://")) {
                return value;
            }
        }
        return null;
    }

    private URI parseAllowed(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid sticker url");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sticker url must be https");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_HOSTS.stream()
                .anyMatch(item -> host.equals(item) || host.endsWith("." + item));
        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "sticker host is not allowed: " + host);
        }
        return uri;
    }

    private static String fileNameOf(URI uri, String contentType) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank() || name.length() > 120) {
            return "sticker" + extensionOf(contentType);
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /** 下载结果。 */
    public record Download(byte[] content, String contentType, String filename) {
    }

    private record CacheEntry(List<String> items, long at) {
    }
}
