package com.veryal.application.file;

import com.veryal.common.core.TimeProvider;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 定期清理上传留下的垃圾：孤儿文件与放弃的分片会话。
 *
 * <p>两类垃圾都是「用户操作到一半」的产物，接口层拿不到取消信号：</p>
 * <ul>
 *   <li>选了图又关掉发布框：文件已经落盘，但没有任何动态引用；</li>
 *   <li>动态被删掉：媒体文件随之变成孤儿（删除动态只软删数据，不动存储）；</li>
 *   <li>分片传到一半关页面：暂存目录留在磁盘上。</li>
 * </ul>
 *
 * <p>保留 24 小时再动手：给「先上传、过一会儿再发布」和跨天续传留出余量。
 * 单轮最多处理 {@link #BATCH} 个，2 vCPU 上删文件也别一次吃掉太多 IO。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanupTask {

    /** 上传超过这个时间还没被任何业务引用，就按孤儿文件处理。 */
    private static final Duration ORPHAN_RETENTION = Duration.ofHours(24);
    /** 分片会话同样保留一天，允许跨天续传。 */
    private static final Duration STALE_UPLOAD_RETENTION = Duration.ofHours(24);
    private static final int BATCH = 200;

    private final FileRepository fileRepository;
    private final FileApplicationService fileApplicationService;
    private final ChunkedUploadService chunkedUploadService;
    private final TimeProvider timeProvider;

    /** 启动一分钟后先扫一轮（清理上次运行留下的垃圾），之后每 6 小时一次。 */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 6 * 60 * 60_000L)
    public void cleanup() {
        try {
            int files = purgeOrphans(timeProvider.now().minus(ORPHAN_RETENTION));
            int uploads = chunkedUploadService.discardStale(STALE_UPLOAD_RETENTION);
            if (files > 0 || uploads > 0) {
                log.info("storage cleanup done: orphanFiles={}, staleUploads={}", files, uploads);
            }
        } catch (RuntimeException e) {
            // 定时任务抛异常会被框架吞掉并只打一行 warn，这里留一条明确的日志
            log.error("storage cleanup failed", e);
        }
    }

    private int purgeOrphans(Instant threshold) {
        List<StoredFile> orphans = fileRepository.findOrphans(threshold, BATCH);
        int removed = 0;
        for (StoredFile file : orphans) {
            try {
                fileApplicationService.deleteOrphan(file.id());
                removed++;
            } catch (RuntimeException e) {
                log.warn("failed to purge orphan file: fileId={}", file.id(), e);
            }
        }
        return removed;
    }
}
