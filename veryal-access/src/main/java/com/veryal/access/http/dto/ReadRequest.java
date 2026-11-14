package com.veryal.access.http.dto;

import jakarta.validation.constraints.Min;

/**
 * 阅读回执请求。
 */
public record ReadRequest(@Min(0) long readSequence) {
}
