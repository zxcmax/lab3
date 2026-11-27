package ru.sfu.testing.subscriptions.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiException extends ResponseStatusException {
    public ApiException(HttpStatus status, String message) {
        super(status, message);
    }
}
