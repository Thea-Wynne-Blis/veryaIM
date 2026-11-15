package com.veryal.domain.report;

/**
 * 举报处理状态。
 */
public enum ReportStatus {

    PENDING(0),
    PROCESSING(1),
    HANDLED(2),
    REJECTED(3);

    private final int code;

    ReportStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ReportStatus fromCode(int code) {
        for (ReportStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return PENDING;
    }
}
