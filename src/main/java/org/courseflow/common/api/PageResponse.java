package org.courseflow.common.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {
  public PageResponse {
    content = content == null ? null : Collections.unmodifiableList(new ArrayList<>(content));
  }

  @Override
  public List<T> content() {
    return content == null ? null : Collections.unmodifiableList(content);
  }

  public static <T> PageResponse<T> from(Page<T> source) {
    return new PageResponse<>(
        source.getContent(),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages(),
        source.isFirst(),
        source.isLast());
  }
}
