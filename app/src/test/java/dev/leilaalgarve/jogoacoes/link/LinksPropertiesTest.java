package dev.leilaalgarve.jogoacoes.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-041: links.base-url is validated at startup -- a missing or non-absolute base stops
 * the context instead of producing e-mails with broken links -- and a trailing slash on it
 * doesn't double the slash in the link.
 */
class LinksPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LinksConfiguration.class);

    @Test
    void missingBaseUrlStopsTheContext() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().isInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080/api", "/api", "ftp://localhost/api", " "})
    void nonAbsoluteHttpBaseUrlStopsTheContext(String baseUrl) {
        runner.withPropertyValues("links.base-url=" + baseUrl)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/api", "http://localhost:8080/api/"})
    void loginLinkUrlIsBaseUrlPlusLoginLinksPath(String baseUrl) {
        runner.withPropertyValues("links.base-url=" + baseUrl)
                .run(context -> assertThat(context.getBean(LinksProperties.class).loginLinkUrl("abc"))
                        .isEqualTo("http://localhost:8080/api/login-links/abc"));
    }
}
