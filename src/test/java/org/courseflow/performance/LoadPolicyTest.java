package org.courseflow.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoadPolicyTest {
  @Test
  void internalErrorsTimeoutsAndInvalidResponsesAreNotOverload() {
    assertFalse(LoadPolicy.controlledOverload(new LoadRunner.Sample(0, "read", 1, 200, 500, "")));
    assertFalse(LoadPolicy.controlledOverload(new LoadRunner.Sample(0, "read", 1, 200, 0, "HttpTimeoutException")));
    assertFalse(LoadPolicy.controlledOverload(new LoadRunner.Sample(0, "read", 1, 200, 503, "INVALID_RESPONSE")));
    assertTrue(LoadPolicy.controlledOverload(new LoadRunner.Sample(0, "read", 1, 200, 503, "")));
  }
}
