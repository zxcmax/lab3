package ru.sfu.testing.subscriptions.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfu.testing.subscriptions.data.PaymentEntity;
import ru.sfu.testing.subscriptions.data.PaymentRepository;
import ru.sfu.testing.subscriptions.data.SubscriptionEntity;
import ru.sfu.testing.subscriptions.data.SubscriptionRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@Service
public class ExpenseService {
    private final PaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public ExpenseService(PaymentRepository payments, SubscriptionRepository subscriptions, Clock clock) {
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional
    public Payment record(UUID owner, PaymentInput input) {
        if (input.paidOn().isAfter(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment date cannot be in the future");
        }
        SubscriptionEntity subscription = subscriptions.lockForChange(input.subscriptionId(), owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subscription not found"));
        return payments.save(new PaymentEntity(subscription, input)).toModel();
    }

    @Transactional(readOnly = true)
    public List<Payment> list(UUID owner, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Date range must contain 1 to 366 days");
        }
        return payments.findAllByOwnerIdAndPaidOnBetweenOrderByPaidOnAscIdAsc(owner, from, to)
                .stream().map(PaymentEntity::toModel).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseReport summarize(UUID owner, LocalDate from, LocalDate to) {
        List<Payment> entries = list(owner, from, to);
        Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Payment entry : entries) {
            byCategory.merge(entry.category(), entry.amount(), BigDecimal::add);
            total = total.add(entry.amount());
        }
        return new ExpenseReport("RUB", from, to, total, entries.size(), Collections.unmodifiableMap(byCategory));
    }
}
