package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 05-048: how one line of a list becomes a domain, is left out, or is skipped as invalid. */
class DisposableDomainLineProcessorTest {

    private final DisposableDomainLineProcessor processor = new DisposableDomainLineProcessor();

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "# a comment", "   # an indented comment"})
    void blankLinesAndCommentsAreLeftOut(String line) {
        assertThat(processor.process(line)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "mailinator.com|mailinator.com",
            "  Mailinator.COM  |mailinator.com",
            "sub.mail-inator.co.uk|sub.mail-inator.co.uk",
            "ação.test|xn--ao-siap.test",
            "10minutemail.com|10minutemail.com"})
    void aDomainIsTrimmedLowerCasedAndInAsciiForm(String line, String domain) {
        assertThat(processor.process(line)).isEqualTo(domain);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not a domain",
            "localhost",
            "-starts-with-hyphen.com",
            "ends-with-hyphen-.com",
            "under_score.com",
            "double..dot.com",
            ".leading-dot.com",
            "trailing-dot.com.",
            "user@mailinator.com",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.com"})
    void aLineThatIsNotADomainIsSkipped(String line) {
        assertThatThrownBy(() -> processor.process(line))
                .isInstanceOf(InvalidDisposableDomainLineException.class)
                .hasMessageContaining(line.strip());
    }

    @org.junit.jupiter.api.Test
    void aDomainLongerThan253CharactersIsSkipped() {
        String label = "a".repeat(63);
        String tooLong = String.join(".", label, label, label, label) + ".com";

        assertThatThrownBy(() -> processor.process(tooLong)).isInstanceOf(InvalidDisposableDomainLineException.class);
    }
}
