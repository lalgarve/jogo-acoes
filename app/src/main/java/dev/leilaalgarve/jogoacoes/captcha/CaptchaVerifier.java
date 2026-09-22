package dev.leilaalgarve.jogoacoes.captcha;

public interface CaptchaVerifier {

    boolean verify(String token);
}
