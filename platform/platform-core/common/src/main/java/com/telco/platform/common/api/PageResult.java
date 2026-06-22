package com.telco.platform.common.api;

import java.util.List;

/**
 * Offset-based pagination envelope (the default pagination style per ADR-015).
 *
 * @param content       page contents
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of matching elements across all pages
 * @param totalPages    total number of pages
 * @param <T>           element type
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
