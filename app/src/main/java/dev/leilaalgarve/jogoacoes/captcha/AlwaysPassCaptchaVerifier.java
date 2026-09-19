package dev.leilaalgarve.jogoacoes.captcha;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Bypasses ALTCHA verification entirely -- only active in the {@code blackbox} profile
 * (spec 05-014), where there's no frontend to solve the real proof-of-work challenge and
 * captcha isn't what a blackbox/Selenium test is meant to exercise.
 */
@Service
@ConditionalOnProperty(name = "captcha.verifier", havingValue = "always-pass")
public class AlwaysPassCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean verify(String token) {
        return true;
    }
}
