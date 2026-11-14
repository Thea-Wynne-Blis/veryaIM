package com.veryal.domain.file;

/**
 * 文件生命周期状态。
 */
public enum FileStatus {

    ACTIVE(0),
    DELETED(1);

    private final int code;

    FileStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FileStatus fromCode(int code) {
        return code == DELETED.code ? DELETED : ACTIVE;
    }
}
