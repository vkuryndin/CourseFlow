package org.courseflow.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class PasswordResetExecutorConfig {

  @Bean(name = "passwordResetExecutor", defaultCandidate = false)
  @DependsOn("passwordResetService") // Keep the service and its dependencies alive while draining.
  public ThreadPoolTaskExecutor passwordResetExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(64);
    executor.setThreadNamePrefix("password-reset-");
    executor.setPrestartAllCoreThreads(true);
    // Never run reset work on the HTTP thread when the queue is full.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    // Drain accepted tasks during normal shutdown; do not interrupt SMTP calls.
    // The wait is bounded; a forced process kill can still lose queued work.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }
}
