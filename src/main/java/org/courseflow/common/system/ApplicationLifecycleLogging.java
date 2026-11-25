package org.courseflow.common.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogging {
  private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogging.class);

  @EventListener(ApplicationReadyEvent.class)
  public void ready() {
    log.info("event=application_ready");
  }

  @EventListener(ContextClosedEvent.class)
  public void stopping() {
    log.info("event=application_shutdown_started");
  }
}
