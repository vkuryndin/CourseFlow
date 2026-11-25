package org.courseflow.common.validation;

import java.net.URI;
import org.courseflow.common.exception.BadRequestException;

/** Validates links displayed by clients; this does not fetch remote resources. */
public final class HttpUrls {
  private HttpUrls() {}

  public static String optional(String value, String field) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    try {
      URI uri = URI.create(normalized);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null) {
        throw new IllegalArgumentException();
      }
      return normalized;
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException(field + " must be an absolute HTTP or HTTPS URL");
    }
  }
}
