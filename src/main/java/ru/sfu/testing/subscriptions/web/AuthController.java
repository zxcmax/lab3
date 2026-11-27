package ru.sfu.testing.subscriptions.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.sfu.testing.subscriptions.service.AuthService;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Account register(@Valid @RequestBody Credentials input) {
        return auth.register(input);
    }

    @PostMapping("/login")
    ResponseEntity<Login> login(@Valid @RequestBody Credentials input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(input));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal(expression = "attributes['session']") Session session) {
        auth.logout(session);
    }
}
