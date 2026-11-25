package org.courseflow.common.exception;

/** Compare the client snapshot after acquiring the existing mutation lock. */
public final class EditVersion {
  private EditVersion() {}

  public static void require(Long expected, long actual) {
    if (expected == null) {
      throw new BadRequestException("version is required when updating an existing resource");
    }
    if (expected.longValue() != actual) {
      throw new org.springframework.dao.OptimisticLockingFailureException(
          "Resource has changed; reload before editing");
    }
  }
}
