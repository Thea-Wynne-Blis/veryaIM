package com.veryal.access.http.sticker;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 网络表情包来源（斗图啦）。
 *
 * <p>站点本身是开放可抓取的：搜索结果页把每张表情放在
 * {@code <img class="image_dtb" src="https://img.doutupk.com/...">} 里。
 * 这里优先按这个类名取图，站点改版时再退一步按列表结构兜底。</p>
 *
 * <p>安全边界：
 * <ul>
 *   <li>只允许 https 且主机在 {@link #ALLOWED_HOSTS} 白名单内，杜绝把接口变成 SSRF 跳板；</li>
 *   <li>不跟随重定向，下载体积硬上限 8MB，超限即中断；</li>
 *   <li>只接受 {@code image/*}，SVG 一律拒绝（可执行脚本）。</li>
 * </ul>
 * 搜索结果做进程内缓存：30 分钟内直接命中，过期后先返回旧结果并在后台刷新，
 * 这样用户不必为「图源站慢」买单；同一关键词的并发搜索只回源一次。
 * 图片同样缓存并预取，同一张图并发只回源一次，面板里的预览图还会
 * 由 {@code /api/v1/stickers/image} 同源中转，避开客户端直连第三方 CDN 卡住的问题。</p>
 */
@Slf4j
@Component
public class StickerSource {

    private static final Set<String> ALLOWED_HOSTS = Set.of("img.doutupk.com", "img.doutula.com");
    /** 一页给 36 张：够挑；面板只对前 12 张 eager 加载，其余滚动到再取。 */
    private static final int MAX_RESULTS = 36;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_CACHE = 200;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    /** 过期但还能顶一段时间的上限；超过就同步回源。 */
    private static final long CACHE_STALE_MS = 6 * 60 * 60 * 1000L;
    /** 启动预热的词：正好覆盖「斗图」页签上的热门词，第一次点就能直接出图。 */
    private static final List<String> WARM_WORDS = List.of("无语", "哈哈", "猫", "打脸", "加油", "狗头");
    /**
     * 搜到结果后顺手预取前几张图：图源站单张 TTFB 有时要 3~4 秒，
     * 等用户点选才去下载就会明显「点了没反应」。预取后多数点击直接命中。
     */
    private static final int PREFETCH_COUNT = 12;
    /** 预取自己的并发上限：留给浏览器预览至少一半下载额度，预热不会把面板堵住。 */
    private static final int PREFETCH_CONCURRENCY = 8;
    /** 回源并发：一次面板会同时要十几张，给足并发才能在一轮 CDN 往返内出图。 */
    private static final int DOWNLOAD_CONCURRENCY = 24;
    private static final long DOWNLOAD_WAIT_SECONDS = 20;
    /** 单张图源站偶尔要 10~17 秒（大图 + 慢 CDN），给足时间再判失败。 */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(20);
    /** 图片字节缓存按条目数限制：6 个热词 × 12 张预取就要 72 条，留足余量。 */
    private static final int MAX_IMAGE_CACHE = 240;
    /** 再按总字节兜底，避免有人用超大图把内存撑爆。 */
    private static final long MAX_IMAGE_CACHE_BYTES = 32L * 1024 * 1024;
    /**
     * 结果项选择器：正常情况是 {@code img.image_dtb}；
     * 站点换版后类名变化时退一步按列表结构找图，避免整个功能直接失联。
     */
    private static final String SELECTOR = "img.image_dtb";
    private static final String FALLBACK_SELECTOR = "a.col-xs-6 img, .pic-content img, img.img-responsive";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String searchEndpoint;
    private final boolean warmUpEnabled;

    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    /** 进行中的回源请求：同一关键词并发搜索只打一次图源站。 */
    private final Map<String, CompletableFuture<List<String>>> inflight =
            new ConcurrentHashMap<>();
    /** 后台刷新中的关键词，避免过期缓存被反复触发刷新。 */
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    /** 已下载过的图片字节（LRU）：用户点选时不必再等图源站。 */
    private final Map<String, Download> imageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Download> eldest) {
                    return size() > MAX_IMAGE_CACHE;
                }
            });
    private final Set<String> prefetching = ConcurrentHashMap.newKeySet();
    /** 同一张图只回源一次：预取与浏览器预览会同时命中同一个地址。 */
    private final Map<String, CompletableFuture<Download>> imageInflight =
            new ConcurrentHashMap<>();
    private final Semaphore downloadSlots = new Semaphore(DOWNLOAD_CONCURRENCY);
    private final Semaphore prefetchSlots = new Semaphore(PREFETCH_CONCURRENCY);
    /**
     * 预热 / 刷新 / 预取都走虚拟线程。
     *
     * <p>生产机只有 2 核，公共 ForkJoinPool 只有 1 个工作线程：用它跑网络 IO 时，
     * 预取会全部排队在一字长蛇后面，面板打开时缓存还是空的（实测要等好几分钟）。
     * 虚拟线程数量由下载并发信号量约束，不会把图源站打爆。</p>
     */
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    private long imageCacheBytes;

    public StickerSource(
            @Value("${veryal.sticker.search-endpoint:https://www.doutula.com/search}")
            String searchEndpoint,
            @Value("${veryal.sticker.warm-up:true}") boolean warmUpEnabled) {
        this.searchEndpoint = searchEndpoint == null || searchEndpoint.isBlank()
                ? "https://www.doutula.com/search" : searchEndpoint.trim();
        this.warmUpEnabled = warmUpEnabled;
    }

    @PreDestroy
    void shutdown() {
        background.shutdownNow();
    }

    /**
     * 启动后在后台预热常用词：部署重启后第一次点开「斗图」就能直接出图。
     *
     * <p>失败只会打日志，绝不影响应用启动；测试环境通过
     * {@code veryal.sticker.warm-up=false} 关闭。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!warmUpEnabled) {
            return;
        }
        for (String word : WARM_WORDS) {
            background.execute(() -> {
                try {
                    search(word, 1);
                } catch (RuntimeException e) {
                    log.debug("sticker warm-up failed: {}", word, e);
                }
            });
        }
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
        if (cached != null) {
            long age = System.currentTimeMillis() - cached.at();
            if (age < CACHE_TTL_MS) {
                return cached.items();
            }
            // 过期了也先返回旧图，同时后台刷新：用户感知不到图源站那 1~3 秒
            if (age < CACHE_STALE_MS) {
                refreshAsync(cacheKey, kw, safePage);
                return cached.items();
            }
        }
        return load(cacheKey, kw, safePage);
    }

    /** 回源并在同关键词上做单飞：后到的请求等前一个的结果，不再重复抓页面。 */
    private List<String> load(String cacheKey, String keyword, int page) {
        CompletableFuture<List<String>> mine = new CompletableFuture<>();
        CompletableFuture<List<String>> running = inflight.putIfAbsent(cacheKey, mine);
        if (running != null) {
            try {
                return running.get(12, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("join sticker search failed: {}", cacheKey, e);
            }
        }
        try {
            List<String> items = fetch(keyword, page);
            cache.put(cacheKey, new CacheEntry(items, System.currentTimeMillis()));
            mine.complete(items);
            prefetch(items);
            return items;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(cacheKey, mine);
        }
    }

    private void refreshAsync(String cacheKey, String keyword, int page) {
        if (!refreshing.add(cacheKey)) {
            return;
        }
        background.execute(() -> {
            try {
                List<String> items = fetch(keyword, page);
                cache.put(cacheKey, new CacheEntry(items, System.currentTimeMillis()));
                prefetch(items);
            } catch (RuntimeException e) {
                log.debug("sticker cache refresh failed: {}", cacheKey, e);
            } finally {
                refreshing.remove(cacheKey);
            }
        });
    }

    /** 后台预取前排图片；失败只打日志，不影响搜索结果。 */
    private void prefetch(List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        int limit = Math.min(items.size(), PREFETCH_COUNT);
        for (int i = 0; i < limit; i++) {
            String url = items.get(i);
            if (imageCache.containsKey(url) || !prefetching.add(url)) {
                continue;
            }
            background.execute(() -> {
                boolean acquired = false;
                try {
                    prefetchSlots.acquire();
                    acquired = true;
                    if (!imageCache.containsKey(url)) {
                        cacheImage(url, download(url));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    log.debug("sticker prefetch failed: {}", url, e);
                } finally {
                    if (acquired) {
                        prefetchSlots.release();
                    }
                    prefetching.remove(url);
                }
            });
        }
    }

    /** 写图片缓存；总量超限时整体清空，简单但不会让内存无上限增长。 */
    private void cacheImage(String key, Download image) {
        if (image.content().length > MAX_IMAGE_CACHE_BYTES) {
            return;
        }
        synchronized (imageCache) {
            Download previous = imageCache.put(key, image);
            if (previous != null) {
                imageCacheBytes -= previous.content().length;
            }
            imageCacheBytes += image.content().length;
            if (imageCacheBytes > MAX_IMAGE_CACHE_BYTES) {
                imageCache.clear();
                imageCache.put(key, image);
                imageCacheBytes = image.content().length;
            }
        }
    }

    /** 预览图中转：命中缓存直接返回，否则与预取/其它预览合并成一次回源。 */
    public Download preview(String rawUrl) {
        return download(rawUrl);
    }

    /**
     * 下载一张表情图，调用方负责落库。
     *
     * <p>同一张图并发只回源一次：打开面板时十几张图同时请求，其中一部分已在预取，
     * 合并后既省图源站带宽，也不会因为重复下载把耗时的几秒叠加起来。</p>
     */
    public Download download(String rawUrl) {
        URI uri = parseAllowed(rawUrl);
        String key = uri.toString();
        Download cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<Download> mine = new CompletableFuture<>();
        CompletableFuture<Download> running = imageInflight.putIfAbsent(key, mine);
        if (running != null) {
            return await(running);
        }
        boolean acquired = false;
        try {
            acquired = downloadSlots.tryAcquire(DOWNLOAD_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "sticker source is busy, retry later");
            }
            Download result = fetchImage(uri, key);
            mine.complete(result);
            return result;
        } catch (BusinessException e) {
            mine.completeExceptionally(e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BusinessException failure = new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "sticker download interrupted");
            mine.completeExceptionally(failure);
            throw failure;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            if (acquired) {
                downloadSlots.release();
            }
            imageInflight.remove(key, mine);
        }
    }

    /** 等别的线程把同一张图下完；失败时把原始业务错误透传出去。 */
    private Download await(CompletableFuture<Download> running) {
        try {
            return running.get(DOWNLOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker download interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException business) {
                throw business;
            }
            log.warn("sticker download joined failure", e.getCause());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker download failed");
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "sticker download timed out");
        }
    }

    /** 真正回源取字节；校验白名单、类型与体积。 */
    private Download fetchImage(URI uri, String key) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; veryal/1.0)")
                .header("Accept", "image/*")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.debug("sticker image returned {} for {}", response.statusCode(), uri);
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
            Download result = new Download(content, contentType, fileNameOf(uri, contentType));
            cacheImage(key, result);
            return result;
        } catch (IOException e) {
            log.warn("sticker download failed: url={}", uri, e);
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
