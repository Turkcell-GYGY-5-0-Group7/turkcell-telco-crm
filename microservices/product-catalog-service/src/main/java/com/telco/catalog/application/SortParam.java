package com.telco.catalog.application;

import com.telco.platform.common.exception.ValidationException;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses the optional {@code sort} request parameter of the form {@code field,asc|desc}
 * (direction optional, defaulting to {@code desc}) against a per-handler whitelist of sortable
 * properties. Absent input falls back to {@code createdAt,desc} (PDF Section 12). Unknown
 * properties or malformed values raise the platform {@link ValidationException}, producing the
 * standard 400 validation error shape.
 */
public final class SortParam {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final String EXPECTED_FORMAT = "field,asc|desc";

    private SortParam() {
    }

    public static Sort parse(String sort, Set<String> allowedProperties) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new ValidationException("Malformed sort parameter",
                    Map.of("sort", sort, "expectedFormat", EXPECTED_FORMAT));
        }
        String property = parts[0].trim();
        if (!allowedProperties.contains(property)) {
            throw new ValidationException("Unsupported sort property: " + property,
                    Map.of("sort", sort,
                            "allowedProperties", allowedProperties.stream().sorted().toList()));
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            String dir = parts[1].trim().toLowerCase(Locale.ROOT);
            switch (dir) {
                case "asc" -> direction = Sort.Direction.ASC;
                case "desc" -> direction = Sort.Direction.DESC;
                default -> throw new ValidationException("Malformed sort direction: " + parts[1],
                        Map.of("sort", sort, "expectedFormat", EXPECTED_FORMAT));
            }
        }
        return Sort.by(direction, property);
    }
}
