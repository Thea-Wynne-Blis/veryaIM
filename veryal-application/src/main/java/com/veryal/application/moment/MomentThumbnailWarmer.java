package com.veryal.application.moment;

import com.veryal.application.file.FileApplicationService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 发布之后预热主缩略图。
 *
 * <p>首屏一次要显示多张图，冷生成一张 640 缩略图在 2 核机器上要几百毫秒；
 * 放到发布之后、用单线程串行做，等好友打开信息流时就是缓存命中，
 * 也不会跟消息链路抢 CPU。</p>
 *
 * <p>队列有界，满了直接丢弃：缩略图只是缓存，用户点开时仍可按需生成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MomentThumbnailWarmer {

    private static final int QUEUE_CAPACITY = 64;

    private final FileApplicationService fileApplicationService;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "moment-thumb-warmer");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    /** 排队生成主缩略图；不阻塞调用方，失败只记 debug。 */
    public void warm(long fileId) {
        executor.execute(() -> {
            try {
                fileApplicationService.thumbnailGranted(fileId,
                        FileApplicationService.THUMB_MASTER_SIDE);
            } catch (RuntimeException e) {
                log.debug("thumbnail warmup skipped: fileId={}, reason={}", fileId, e.getMessage());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
