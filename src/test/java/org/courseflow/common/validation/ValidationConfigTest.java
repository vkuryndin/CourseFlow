package org.courseflow.common.validation;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.courseflow.testing.Fixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.constraints.Future;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ValidationConfigTest {
  private record FutureValue(@Future Instant value) {}

  @Test
  void validatorUsesInjectedClock() {
    var validator = new ValidationConfig().defaultValidator(CLOCK);
    validator.afterPropertiesSet();
    try {
      assertTrue(validator.validate(new FutureValue(NOW.plusSeconds(1))).isEmpty());
      assertFalse(validator.validate(new FutureValue(NOW)).isEmpty());
    } finally {
      validator.close();
    }
  }
}
