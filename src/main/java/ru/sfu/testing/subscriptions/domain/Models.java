package ru.sfu.testing.subscriptions.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class Models {
    private Models() {
    }

    public enum Category { MEDIA, EDUCATION, SOFTWARE, OTHER }
    public enum BillingPeriod { MONTHLY, YEARLY }
    public enum Status { ACTIVE, PAUSED, CANCELLED }

    public record Credentials(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password) {}

    public record Account(UUID id, String email) {}

    public record Login(String accessToken, Instant expiresAt) {}

    public record Session(UUID accountId, String tokenHash) {}

    public record SubscriptionInput(
            @NotBlank @Size(max = 120) String name,
            @NotNull Category category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotNull BillingPeriod billingPeriod,
            @NotNull LocalDate nextChargeDate,
            @NotNull Status status) {}

    public record Subscription(UUID id, String name, Category category, BigDecimal amount,
                               BillingPeriod billingPeriod, LocalDate nextChargeDate, Status status) {}

    public record PaymentInput(
            @NotNull UUID subscriptionId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn) {}

    public record Payment(UUID id, UUID subscriptionId, String subscriptionName, Category category,
                          BigDecimal amount, LocalDate paidOn) {}

    public record ExpenseReport(String currency, LocalDate from, LocalDate to, BigDecimal total,
                                long paymentCount, Map<Category, BigDecimal> byCategory) {}
}
