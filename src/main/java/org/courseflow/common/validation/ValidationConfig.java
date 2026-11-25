package org.courseflow.common.validation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class ValidationConfig {
  @Bean
  @Primary
  public LocalValidatorFactoryBean defaultValidator(Clock clock) {
    var validator = new LocalValidatorFactoryBean();
    validator.setConfigurationInitializer(
        configuration -> configuration.clockProvider(() -> clock));
    return validator;
  }
}
