package com.telco.platform.common.api;

import java.util.List;

/**
 * Cursor-based pagination envelope for large datasets (per ADR-015).
 *
 * @param content    page contents
 * @param nextCursor opaque cursor to fetch the next page; null when there is none
 * @param hasNext    whether a further page exists
 * @param limit      requested page size
 * @param <T>        element type
 */
public record CursorPage<T>(List<T> content, String nextCursor, boolean hasNext, int limit) {
}
