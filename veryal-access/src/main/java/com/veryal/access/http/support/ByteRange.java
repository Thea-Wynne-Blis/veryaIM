package com.veryal.access.http.support;

/**
 * HTTP Range 解析：只支持单区间 {@code bytes=start-end}（视频播放与断点续传都是这种）。
 *
 * <p>多区间、非法语法一律忽略 Range 返回整份内容，这是 RFC 9110 允许的行为；
 * 只有「语法正确但越界」才回 416，浏览器据此知道请求位置无效。</p>
 */
public final class ByteRange {

    private ByteRange() {
    }

    public record Range(long start, long end) {

        public long length() {
            return end - start + 1;
        }
    }

    /**
     * @return 需要回传的区间；{@code null} 表示按整份内容返回
     * @throws IllegalStateException 语法正确但完全越界（应回 416）
     */
    public static Range parse(String header, long size) {
        if (header == null || header.isBlank() || size <= 0) {
            return null;
        }
        String value = header.trim();
        if (!value.startsWith("bytes=")) {
            return null;
        }
        String spec = value.substring("bytes=".length()).trim();
        if (spec.contains(",")) {
            // 多区间需要 multipart/byteranges，聊天场景用不到，直接整份返回
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        try {
            if (startText.isEmpty()) {
                // bytes=-500：最后 500 字节
                long suffix = Long.parseLong(endText);
                if (suffix <= 0) {
                    return null;
                }
                long start = Math.max(0, size - suffix);
                return new Range(start, size - 1);
            }
            long start = Long.parseLong(startText);
            if (start < 0 || start >= size) {
                throw new IllegalStateException("range start out of bounds");
            }
            long end = endText.isEmpty() ? size - 1 : Long.parseLong(endText);
            if (end < start) {
                return null;
            }
            return new Range(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
