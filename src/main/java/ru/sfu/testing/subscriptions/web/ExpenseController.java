package ru.sfu.testing.subscriptions.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.sfu.testing.subscriptions.service.ExpenseService;

import java.time.LocalDate;
import java.util.List;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@RestController
@RequestMapping("/api")
class ExpenseController {
    private final ExpenseService expenses;

    ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Payment record(@AuthenticationPrincipal(expression = "attributes['session']") Session session, @Valid @RequestBody PaymentInput input) {
        return expenses.record(session.accountId(), input);
    }

    @GetMapping("/payments")
    List<Payment> list(@AuthenticationPrincipal(expression = "attributes['session']") Session session,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.list(session.accountId(), from, to);
    }

    @GetMapping("/analytics/expenses")
    ExpenseReport summarize(@AuthenticationPrincipal(expression = "attributes['session']") Session session,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.summarize(session.accountId(), from, to);
    }
}
