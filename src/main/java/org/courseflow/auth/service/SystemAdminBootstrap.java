
package org.courseflow.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemAdminBootstrap implements ApplicationRunner {

    private final SystemAdminBootstrapService bootstrapService;

    public SystemAdminBootstrap(
            SystemAdminBootstrapService bootstrapService
    ) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.initialize();
    }
}
