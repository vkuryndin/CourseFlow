package org.courseflow.common.api;

import java.util.Set;

public final class PublicEndpoints {
  private PublicEndpoints() {}

  private static final Set<String> GET =
      Set.of(
          "/api/categories",
          "/api/categories/*",
          "/api/tags",
          "/api/tags/*",
          "/api/courses",
          "/api/courses/*",
          "/api/courses/*/tags",
          "/api/courses/*/modules",
          "/api/courses/*/modules/*",
          "/api/teachers",
          "/api/teachers/*",
          "/api/courses/*/reviews",
          "/api/courses/*/reviews/rating");
  private static final Set<String> POST =
      Set.of(
          "/api/auth/register",
          "/api/auth/login",
          "/api/auth/forgot-password",
          "/api/auth/reset-password");
  private static final Set<String> RATE_LIMITED_POST =
      Set.of(
          "/api/auth/register",
          "/api/auth/login",
          "/api/auth/forgot-password",
          "/api/auth/reset-password",
          "/api/auth/change-password");

  public static String[] getPatterns() {
    return GET.toArray(String[]::new);
  }

  public static String[] postPatterns() {
    return POST.toArray(String[]::new);
  }

  public static boolean isPublicGet(String openApiPath) {
    return GET.contains(openApiPath.replaceAll("\\{[^/]+}", "*"));
  }

  public static boolean isPublicPost(String path) {
    return POST.contains(path);
  }

  public static boolean isRateLimitedPost(String path) {
    return RATE_LIMITED_POST.contains(path);
  }
}
