package com.veryal.domain.file;

/**
 * 分片上传会话状态。
 */
public enum UploadStatus {

    ACTIVE(0),
    COMPLETED(1),
    CANCELED(2);

    private final int code;

    UploadStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static UploadStatus fromCode(int code) {
        for (UploadStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return ACTIVE;
    }
}
