package com.calldesk.calls;

import java.util.List;

public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public PageDto { content = List.copyOf(content); }
}
