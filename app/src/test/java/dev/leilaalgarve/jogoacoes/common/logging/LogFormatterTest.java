package dev.leilaalgarve.jogoacoes.common.logging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogFormatterTest {

    @Test
    void truncatesAListOfMoreThanOneItemToTheFirstPlusRemainingCount() {
        List<String> emails = List.of("joao@exemplo.com", "maria@exemplo.com", "ana@exemplo.com",
                "carlos@exemplo.com", "beatriz@exemplo.com", "lucas@exemplo.com", "paula@exemplo.com",
                "rafael@exemplo.com", "sofia@exemplo.com", "tiago@exemplo.com");

        assertThat(LogFormatter.summarize(emails)).isEqualTo("joao@exemplo.com+[9]");
    }

    @Test
    void truncatesAMapOfMoreThanOneItemToTheFirstEntryPlusRemainingCount() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Sec-CH-UA-Platform", "Windows");
        headers.put("Sec-CH-UA-Mobile", "?0");

        assertThat(LogFormatter.summarize(headers)).isEqualTo("Sec-CH-UA-Platform=Windows+[1]");
    }

    @Test
    void aCollectionWithZeroItemsIsNotTruncated() {
        assertThat(LogFormatter.summarize(List.of())).isEqualTo("[]");
    }

    @Test
    void aCollectionWithOneItemIsNotTruncated() {
        assertThat(LogFormatter.summarize(List.of("joao@exemplo.com"))).isEqualTo("[\"joao@exemplo.com\"]");
    }

    @Test
    void anOrdinaryObjectIsSerializedWhole() {
        record Point(int x, int y) {
        }

        assertThat(LogFormatter.summarize(new Point(1, 2))).isEqualTo("{\"x\":1,\"y\":2}");
    }

    @Test
    void anObjectWithACyclicReferenceFallsBackToToStringInsteadOfThrowing() {
        Node a = new Node("a");
        Node b = new Node("b");
        a.other = b;
        b.other = a;

        assertThatCode(() -> LogFormatter.summarize(a)).doesNotThrowAnyException();
        assertThat(LogFormatter.summarize(a)).isEqualTo(a.toString());
    }

    // Public fields -- Jackson's default visibility only auto-detects public fields, and this
    // needs it to actually attempt the cyclic traversal (and hit the depth-limit guard that
    // makes it throw) instead of finding nothing serializable and returning "{}".
    static class Node {
        public String name;
        public Node other;

        Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Node{" + name + "}";
        }
    }
}
