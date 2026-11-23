package com.veryal.application.file;

/**
 * 旧文件回收：业务对象换图后，把上一条文件从存储里清掉。
 *
 * <p>清理失败不能影响本次保存——残留文件交给 {@code StorageCleanupTask} 兜底。</p>
 */
public final class OrphanFiles {

    private OrphanFiles() {
    }

    public static void dropQuietly(FileApplicationService files, Long previous, Long current) {
        if (previous == null || previous.equals(current)) {
            return;
        }
        try {
            files.deleteOrphan(previous);
        } catch (RuntimeException ignored) {
            // 旧文件清理失败不影响新图生效
        }
    }
}
