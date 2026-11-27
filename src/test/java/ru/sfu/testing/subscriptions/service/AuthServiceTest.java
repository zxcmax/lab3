package ru.sfu.testing.subscriptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sfu.testing.subscriptions.data.AccessTokenEntity;
import ru.sfu.testing.subscriptions.data.AccessTokenRepository;
import ru.sfu.testing.subscriptions.data.AccountEntity;
import ru.sfu.testing.subscriptions.data.AccountRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.sfu.testing.subscriptions.domain.Models.*;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "demo@example.test";
    private static final String PASSWORD = "example-password";
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccessTokenRepository tokens = mock(AccessTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("encoded");
        auth = new AuthService(accounts, tokens, encoder, Clock.fixed(NOW, ZoneOffset.UTC), manager);
    }

    @Test
    void registerNormalizesEmailAndStoresOnlyHash() {
        when(accounts.saveAndFlush(any(AccountEntity.class))).thenAnswer(call -> {
            AccountEntity saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", USER);
            return saved;
        });

        Account account = auth.register(new Credentials("Demo@Example.Test", PASSWORD));

        var entity = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accounts).saveAndFlush(entity.capture());
        assertAll(
                () -> assertEquals(new Account(USER, EMAIL), account),
                () -> assertEquals("encoded", entity.getValue().getPasswordHash()));
    }

    @Test
    void duplicateEmailBecomesConflict() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(accounts).saveAndFlush(any(AccountEntity.class));
        when(accounts.existsByEmail(EMAIL)).thenReturn(true);

        ApiException failure = assertThrows(ApiException.class,
                () -> auth.register(new Credentials(EMAIL, PASSWORD)));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    @Test
    void unrelatedIntegrityFailureIsNotReportedAsDuplicateEmail() {
        var failure = new DataIntegrityViolationException("storage constraint");
        doThrow(failure).when(accounts).saveAndFlush(any(AccountEntity.class));

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> auth.register(new Credentials(EMAIL, PASSWORD))));
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 73})
    void rejectsPasswordOutsideByteBounds(int length) {
        ApiException failure = assertThrows(ApiException.class,
                () -> auth.register(new Credentials(EMAIL, "x".repeat(length))));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verifyNoInteractions(accounts);
    }

    @Test
    void badCredentialsFromAuthenticationManagerAreRejected() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));

        ApiException failure = assertThrows(ApiException.class,
                () -> auth.login(new Credentials(EMAIL, PASSWORD)));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        verifyNoInteractions(tokens);
    }

    @Test
    void validLoginStoresTokenHashAndAuthenticationUsesIt() {
        when(manager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(USER.toString(), null, List.of()));

        Login login = auth.login(new Credentials(EMAIL, PASSWORD));

        var token = ArgumentCaptor.forClass(AccessTokenEntity.class);
        Instant expiry = NOW.plusSeconds(3600);
        verify(tokens).save(token.capture());
        verify(tokens).deleteExpired(NOW);
        AccessTokenEntity saved = token.getValue();
        assertAll(
                () -> assertEquals(expiry, login.expiresAt()),
                () -> assertEquals(expiry, saved.getExpiresAt()),
                () -> assertEquals(USER, saved.getAccountId()),
                () -> assertTrue(login.accessToken().matches("[A-Za-z0-9_-]{43}")),
                () -> assertTrue(saved.getTokenHash().matches("[0-9a-f]{64}")),
                () -> assertNotEquals(login.accessToken(), saved.getTokenHash()));
        when(tokens.findActiveAccountId(saved.getTokenHash(), NOW)).thenReturn(Optional.of(USER));

        Session session = auth.authenticate(login.accessToken()).orElseThrow();
        assertEquals(USER, session.accountId());

        auth.logout(session);
        verify(tokens).revoke(saved.getTokenHash());
    }

    @Test
    void malformedAndMissingTokensNeverReachStorage() {
        assertAll(
                () -> assertTrue(auth.authenticate(null).isEmpty()),
                () -> assertTrue(auth.authenticate("not-a-token").isEmpty()));
        verifyNoInteractions(tokens);
    }

    @Test
    void expiredOrUnrecognizedTokenHasNoSession() {
        when(tokens.findActiveAccountId(anyString(), eq(NOW))).thenReturn(Optional.empty());

        assertTrue(auth.authenticate("x".repeat(43)).isEmpty());
    }

}
