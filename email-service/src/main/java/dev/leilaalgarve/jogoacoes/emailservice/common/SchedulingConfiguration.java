package dev.leilaalgarve.jogoacoes.emailservice.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background work of this service (specs 05-044 and 05-046) runs on Spring's task scheduler,
 * which Spring Boot only sets up when scheduling is enabled.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
