package org.courseflow.integration.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JsonChecksTest {
  @Test
  void missingNullAndWrongTypeAreDistinct() {
    Object body = JsonChecks.parse("{\"value\":null,\"number\":4,\"text\":\"4\",\"decimal\":4.5}");
    assertThrows(IllegalArgumentException.class, () -> JsonChecks.parse("{\"a\":1,\"a\":2}"));
    assertThrows(IllegalArgumentException.class, () -> JsonChecks.parse("{} {}"));
    assertNull(JsonChecks.requiredField(body, "value"));
    assertEquals(4L, JsonChecks.requiredInteger(body, "number"));
    assertThrows(AssertionError.class, () -> JsonChecks.requiredField(body, "missing"));
    assertThrows(AssertionError.class, () -> JsonChecks.requiredInteger(body, "value"));
    assertThrows(AssertionError.class, () -> JsonChecks.requiredInteger(body, "text"));
    assertThrows(AssertionError.class, () -> JsonChecks.requiredInteger(body, "decimal"));
  }
}
