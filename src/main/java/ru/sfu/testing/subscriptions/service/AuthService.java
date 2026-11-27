package ru.sfu.testing.subscriptions.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfu.testing.subscriptions.data.AccountRepository;
import ru.sfu.testing.subscriptions.data.AccountEntity;
import ru.sfu.testing.subscriptions.data.AccessTokenEntity;
import ru.sfu.testing.subscriptions.data.AccessTokenRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@Service
public class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final AccountRepository accounts;
    private final AccessTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final AuthenticationManager authenticationManager;

    public AuthService(AccountRepository accounts, AccessTokenRepository tokens, PasswordEncoder passwords,
                       Clock clock, AuthenticationManager authenticationManager) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clock = clock;
        this.authenticationManager = authenticationManager;
    }

    public Account register(Credentials input) {
        validatePassword(input.password());
        String email = normalizeEmail(input.email());
        AccountEntity account = new AccountEntity(email, passwords.encode(input.password()));
        try {
            AccountEntity saved = accounts.saveAndFlush(account);
            return new Account(saved.getId(), saved.getEmail());
        } catch (DataIntegrityViolationException failure) {
            if (accounts.existsByEmail(email)) {
                throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
            }
            throw failure;
        }
    }

    @Transactional
    public Login login(Credentials input) {
        validatePassword(input.password());
        UUID accountId;
        try {
            var request = UsernamePasswordAuthenticationToken.unauthenticated(
                    normalizeEmail(input.email()), input.password());
            accountId = UUID.fromString(authenticationManager.authenticate(request).getName());
        } catch (AuthenticationException invalid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expires = now.plus(TOKEN_LIFETIME);
        tokens.deleteExpired(now);
        tokens.save(new AccessTokenEntity(hashToken(token), accountId, expires));
        return new Login(token, expires);
    }

    public Optional<Session> authenticate(String token) {
        if (token == null || !TOKEN_SHAPE.matcher(token).matches()) {
            return Optional.empty();
        }
        String hash = hashToken(token);
        return tokens.findActiveAccountId(hash, clock.instant()).map(id -> new Session(id, hash));
    }

    @Transactional
    public void logout(Session session) {
        tokens.revoke(session.tokenHash());
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be 8 to 72 UTF-8 bytes");
        }
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }
}
