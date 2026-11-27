package ru.sfu.testing.subscriptions.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.sfu.testing.subscriptions.service.SubscriptionService;

import java.util.List;
import java.util.UUID;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionController {
    private final SubscriptionService subscriptions;

    SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Subscription create(@AuthenticationPrincipal(expression = "attributes['session']") Session session, @Valid @RequestBody SubscriptionInput input) {
        return subscriptions.create(session.accountId(), input);
    }

    @GetMapping
    List<Subscription> list(@AuthenticationPrincipal(expression = "attributes['session']") Session session) {
        return subscriptions.list(session.accountId());
    }

    @GetMapping("/{id}")
    Subscription get(@AuthenticationPrincipal(expression = "attributes['session']") Session session, @PathVariable UUID id) {
        return subscriptions.get(session.accountId(), id);
    }

    @PutMapping("/{id}")
    Subscription update(@AuthenticationPrincipal(expression = "attributes['session']") Session session, @PathVariable UUID id,
                        @Valid @RequestBody SubscriptionInput input) {
        return subscriptions.update(session.accountId(), id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal(expression = "attributes['session']") Session session, @PathVariable UUID id) {
        subscriptions.delete(session.accountId(), id);
    }
}
