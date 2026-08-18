package io.deployo.jogoacoes.captcha;

import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Self-hosted proof-of-work captcha (decision 6 in docs/iteracao-3.md) -- no third-party
 * service, no fake/stub: {@link #verify(String)} runs the real ALTCHA HMAC verification
 * (same library and algorithm proven in AltchaSmokeTest).
 *
 * There's no frontend widget yet to dictate ALTCHA's usual over-the-wire payload format, so
 * {@link #encodeToken} defines a simple self-contained envelope instead (challenge
 * parameters + signature + solution, base64(JSON)) -- still a real, HMAC-signed, stateless
 * token: the server never needs to have stored the challenge to verify it, exactly like the
 * standard ALTCHA design.
 */
@Service
public class CaptchaService {

    private static final String ALGORITHM = "SHA-256";
    private static final int COST = 50_000;
    private static final int EXPIRES_IN_SECONDS = 300;

    private final String secret;

    public CaptchaService(@Value("${altcha.secret}") String secret) {
        this.secret = secret;
    }

    public Altcha.Challenge createChallenge() {
        try {
            return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                    .algorithm(ALGORITHM)
                    .cost(COST)
                    .hmacSignatureSecret(secret)
                    .expiresInSeconds(EXPIRES_IN_SECONDS));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create captcha challenge", e);
        }
    }

    public String encodeToken(Altcha.Challenge challenge, Altcha.Solution solution) {
        JSONObject envelope = new JSONObject();
        envelope.put("challenge", new JSONObject(challenge.toJson()));
        envelope.put("counter", solution.counter());
        envelope.put("derivedKey", solution.derivedKey());
        envelope.put("time", solution.time());
        return Base64.getEncoder().encodeToString(envelope.toString().getBytes(StandardCharsets.UTF_8));
    }

    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            JSONObject envelope = new JSONObject(new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8));
            Altcha.Challenge challenge = decodeChallenge(envelope.getJSONObject("challenge"));
            Altcha.Solution solution = new Altcha.Solution(
                    envelope.getInt("counter"), envelope.getString("derivedKey"), envelope.getLong("time"));

            Altcha.VerifySolutionResult result = Altcha.verifySolution(
                    challenge, solution, secret, Altcha.kdf(challenge.parameters().algorithm()));
            return result.verified() && !result.expired();
        } catch (Exception e) {
            return false;
        }
    }

    private static Altcha.Challenge decodeChallenge(JSONObject challengeJson) {
        JSONObject params = challengeJson.getJSONObject("parameters");
        Altcha.ChallengeParameters parameters = new Altcha.ChallengeParameters(
                params.getString("algorithm"),
                params.getString("nonce"),
                params.getString("salt"),
                params.getInt("cost"),
                params.getInt("keyLength"),
                params.optString("keyPrefix", null),
                params.optString("keySignature", null),
                params.has("memoryCost") ? params.getInt("memoryCost") : null,
                params.has("parallelism") ? params.getInt("parallelism") : null,
                params.getLong("expiresAt"),
                null);
        return new Altcha.Challenge(parameters, challengeJson.getString("signature"));
    }
}
