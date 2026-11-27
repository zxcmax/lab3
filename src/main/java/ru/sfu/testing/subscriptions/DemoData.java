package ru.sfu.testing.subscriptions;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.sfu.testing.subscriptions.data.AccountRepository;
import ru.sfu.testing.subscriptions.service.AuthService;
import ru.sfu.testing.subscriptions.service.ExpenseService;
import ru.sfu.testing.subscriptions.service.SubscriptionService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@Component
@Profile("demo")
class DemoData implements ApplicationRunner {
    private static final String PASSWORD = "Lab3-demo-2026!";
    private final AccountRepository accounts;
    private final AuthService auth;
    private final SubscriptionService subscriptions;
    private final ExpenseService expenses;
    private final Clock clock;

    DemoData(AccountRepository accounts, AuthService auth, SubscriptionService subscriptions,
             ExpenseService expenses, Clock clock) {
        this.accounts = accounts;
        this.auth = auth;
        this.subscriptions = subscriptions;
        this.expenses = expenses;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(clock);
        if (!accounts.existsByEmail("demo@example.test")) {
            UUID owner = auth.register(new Credentials("demo@example.test", PASSWORD)).id();
            Subscription music = add(owner, "Music Plus", Category.MEDIA, "299.00", BillingPeriod.MONTHLY,
                    today.plusDays(18), Status.ACTIVE);
            Subscription courses = add(owner, "Online Courses", Category.EDUCATION, "799.00", BillingPeriod.MONTHLY,
                    today.plusDays(23), Status.ACTIVE);
            Subscription cloud = add(owner, "Cloud Drive", Category.SOFTWARE, "1490.00", BillingPeriod.YEARLY,
                    today.plusMonths(11), Status.PAUSED);
            pay(owner, music, "299.00", today.minusDays(12));
            pay(owner, music, "299.00", today.minusDays(42));
            pay(owner, courses, "799.00", today.minusDays(7));
            pay(owner, cloud, "1490.00", today.minusDays(20));
        }
        if (!accounts.existsByEmail("demo-other@example.test")) {
            UUID owner = auth.register(new Credentials("demo-other@example.test", PASSWORD)).id();
            Subscription reading = add(owner, "Reading Club", Category.OTHER, "199.00", BillingPeriod.MONTHLY,
                    today.plusDays(27), Status.ACTIVE);
            pay(owner, reading, "199.00", today.minusDays(3));
        }
    }

    private Subscription add(UUID owner, String name, Category category, String amount,
                             BillingPeriod period, LocalDate nextCharge, Status status) {
        return subscriptions.create(owner,
                new SubscriptionInput(name, category, new BigDecimal(amount), period, nextCharge, status));
    }

    private void pay(UUID owner, Subscription subscription, String amount, LocalDate date) {
        expenses.record(owner, new PaymentInput(subscription.id(), new BigDecimal(amount), date));
    }
}
