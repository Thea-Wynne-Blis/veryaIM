package com.veryal.domain.message;

import java.time.Instant;
import java.util.List;

/** 已校验的会话搜索条件；时间区间为左闭右开。 */
public record MessageSearchFilter(List<Integer> types, String pattern, Long senderId,
                                  Instant from, Instant to, boolean linksOnly) {
}
