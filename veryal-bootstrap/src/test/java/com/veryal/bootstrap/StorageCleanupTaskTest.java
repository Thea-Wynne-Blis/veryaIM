package com.veryal.bootstrap;

import com.veryal.application.file.ChunkedUploadService;
import com.veryal.application.file.FileApplicationService;
import com.veryal.application.file.StorageCleanupTask;
import com.veryal.common.core.TimeProvider;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.FileStatus;
import com.veryal.domain.file.StorageType;
import com.veryal.domain.file.StoredFile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储清理：只清理超过保留时间的孤儿文件，单个失败不影响其余，分片会话同样过期回收。
 */
class StorageCleanupTaskTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileApplicationService fileApplicationService = mock(FileApplicationService.class);
    private final ChunkedUploadService chunkedUploadService = mock(ChunkedUploadService.class);
    private final StorageCleanupTask task = new StorageCleanupTask(fileRepository,
            fileApplicationService, chunkedUploadService, TimeProvider.fixed(NOW));

    @Test
    void purgesEveryOrphan() {
        when(fileRepository.findOrphans(any(), anyInt())).thenReturn(List.of(file(10L), file(11L)));

        task.cleanup();

        verify(fileApplicationService).deleteOrphan(10L);
        verify(fileApplicationService).deleteOrphan(11L);
    }

    @Test
    void keepsGoingWhenOneFileFails() {
        when(fileRepository.findOrphans(any(), anyInt())).thenReturn(List.of(file(20L), file(21L)));
        doThrow(new IllegalStateException("storage is down")).when(fileApplicationService).deleteOrphan(20L);

        task.cleanup();

        verify(fileApplicationService).deleteOrphan(21L);
    }

    @Test
    void onlyConsidersFilesOlderThanOneDay() {
        task.cleanup();

        verify(fileRepository).findOrphans(NOW.minus(Duration.ofHours(24)), 200);
    }

    @Test
    void discardsStaleUploadSessions() {
        task.cleanup();

        verify(chunkedUploadService).discardStale(Duration.ofHours(24));
    }

    @Test
    void skipsUploadCleanupWhenRepositoryFails() {
        when(fileRepository.findOrphans(any(), anyInt()))
                .thenThrow(new IllegalStateException("db is down"));

        task.cleanup();

        verify(chunkedUploadService, never()).discardStale(any());
    }

    private static StoredFile file(long id) {
        return new StoredFile(id, 1L, "files/2026/11/19/" + id + ".jpg", "photo.jpg",
                "image/jpeg", 1024, "a".repeat(64), StorageType.LOCAL, FileStatus.ACTIVE,
                NOW.minus(Duration.ofHours(30)), null, null);
    }
}
