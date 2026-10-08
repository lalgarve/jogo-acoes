package dev.leilaalgarve.jogoacoes.email.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Feign client generated from docs/openapi-email-service.yaml (spec 05-034). Active
 * only where {@code email-service.base-url} is set: the Cucumber suites run without
 * email-service (spec 05-034, "Decisões resolvidas"), and so does any profile that doesn't send
 * through it yet.
 *
 * <p>The client package is named as a string on purpose: only EmailServiceGateway may depend on
 * the generated classes (ArchitectureTest).
 */
@Configuration
@ConditionalOnProperty("email-service.base-url")
@EnableFeignClients(basePackages = "dev.leilaalgarve.jogoacoes.email.client.api")
class EmailServiceClientConfiguration {

    static final String API_KEY_HEADER = "X-API-Key";

    /**
     * Every call to email-service carries app's API key. Feign picks up RequestInterceptor beans
     * for all its clients -- fine here, email-service's are the only Feign clients in app.
     */
    @Bean
    RequestInterceptor emailServiceApiKeyInterceptor(@Value("${email-service.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "email-service.api-key (EMAIL_SERVICE_API_KEY) must be set when email-service.base-url is");
        }
        return template -> template.header(API_KEY_HEADER, apiKey);
    }
}
