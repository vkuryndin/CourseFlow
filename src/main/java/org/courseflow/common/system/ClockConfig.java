package org.courseflow.common.system;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean defaultValidator(Clock clock) {
        var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.setConfigurationInitializer(configuration -> configuration.clockProvider(() -> clock));
        return validator;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
