package org.courseflow.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PasswordResetExecutorConfigTest {
  private final PasswordResetExecutorConfig config = new PasswordResetExecutorConfig();

  @Test
  void executorHasBoundedQueueAndFixedWorkers() {
    ThreadPoolTaskExecutor executor = config.passwordResetExecutor();
    executor.initialize();
    try {
      assertEquals(2, executor.getCorePoolSize());
      assertEquals(2, executor.getMaxPoolSize());
      assertEquals(64, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
      assertEquals("password-reset-", executor.getThreadNamePrefix());
      assertTrue(
          executor.getThreadPoolExecutor().getRejectedExecutionHandler()
              instanceof java.util.concurrent.ThreadPoolExecutor.AbortPolicy);
    } finally {
      executor.shutdown();
    }
  }
}
