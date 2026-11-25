package org.courseflow.common.api;

/** Literal substring patterns for criteria LIKE with backslash escape. */
public final class SearchPatterns {
  private SearchPatterns() {}

  public static String containsLiteral(String normalized) {
    return "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
