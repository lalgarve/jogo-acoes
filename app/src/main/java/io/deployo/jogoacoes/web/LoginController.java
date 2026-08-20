package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.LoginApi;
import io.deployo.jogoacoes.api.model.CompleteRegistrationRequest;
import io.deployo.jogoacoes.api.model.LoginResult;
import io.deployo.jogoacoes.api.model.RequestLoginLinkRequest;
import io.deployo.jogoacoes.service.LoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController implements LoginApi {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    public ResponseEntity<LoginResult> consumeLoginLink(String token) {
        LoginResult result = loginService.consumeLoginLink(token);
        if (result == null) {
            return ResponseEntity.status(202).build();
        }
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<LoginResult> completeRegistration(String token, CompleteRegistrationRequest completeRegistrationRequest) {
        return ResponseEntity.ok(loginService.completeRegistration(token, completeRegistrationRequest.getName()));
    }

    @Override
    public ResponseEntity<Void> requestLoginLink(RequestLoginLinkRequest requestLoginLinkRequest) {
        loginService.requestLoginLink(requestLoginLinkRequest.getEmail());
        return ResponseEntity.status(202).build();
    }
}
