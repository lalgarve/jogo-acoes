package io.deployo.jogoacoes;

import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the ALTCHA v2 create/solve/verify round-trip actually works against this
 * project's dependency versions — not yet wired into request_competition_entry.feature.
 */
class AltchaSmokeTest {

    private static final String SECRET = "test-secret";

    @Test
    void solvedChallengeVerifiesSuccessfully() throws Exception {
        Altcha.Challenge challenge = Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                .algorithm("SHA-256")
                .cost(1_000)
                .hmacSignatureSecret(SECRET)
                .expiresInSeconds(600));

        Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf("SHA-256"));

        Altcha.VerifySolutionResult result = Altcha.verifySolution(challenge, solution, SECRET, Altcha.kdf("SHA-256"));

        assertThat(result.verified()).isTrue();
    }

    @Test
    void tamperedSolutionFailsVerification() throws Exception {
        Altcha.Challenge challenge = Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                .algorithm("SHA-256")
                .cost(1_000)
                .hmacSignatureSecret(SECRET)
                .expiresInSeconds(600));

        Altcha.Solution wrongSolution = new Altcha.Solution(-1, "not-the-right-key", 0L);

        Altcha.VerifySolutionResult result = Altcha.verifySolution(challenge, wrongSolution, SECRET, Altcha.kdf("SHA-256"));

        assertThat(result.verified()).isFalse();
    }
}
