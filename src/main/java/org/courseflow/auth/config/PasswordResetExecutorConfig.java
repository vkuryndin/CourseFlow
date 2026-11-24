package org.courseflow.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class PasswordResetExecutorConfig {

    @Bean(name = "passwordResetExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor passwordResetExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("password-reset-");
        executor.setPrestartAllCoreThreads(true);
        // Never run reset work on the HTTP thread when the queue is full.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setStrictEarlyShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
