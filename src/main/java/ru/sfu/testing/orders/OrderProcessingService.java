package ru.sfu.testing.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static ru.sfu.testing.orders.OrderData.*;
import static ru.sfu.testing.orders.OrderDependencies.*;

public final class OrderProcessingService {
    private final Repository orders;
    private final Inventory inventory;
    private final PaymentGateway payments;
    private final DiscountEngine discounts;
    private final TaxCalculator tax;
    private final Notification notifications;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final Delayer delayer;
    private final Options options;
    private final System.Logger logger;

    public OrderProcessingService(Repository orders, Inventory inventory, PaymentGateway payments,
                                  DiscountEngine discounts, TaxCalculator tax, Notification notifications,
                                  UnitOfWork unitOfWork, Clock clock, Delayer delayer, Options options,
                                  System.Logger logger) {
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
        this.discounts = discounts;
        this.tax = tax;
        this.notifications = notifications;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.delayer = delayer;
        this.options = options;
        this.logger = logger;
    }

    public CompletionStage<Result> placeOrderAsync(Request request, CancellationToken token) {
        Objects.requireNonNull(request, "request");
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return completedFuture(fail(Failure.VALIDATION, "Idempotency key is required."));
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            return completedFuture(fail(Failure.VALIDATION, "Order must contain at least one line."));
        }
        if (request.lines().size() > options.maxLines()) {
            return completedFuture(fail(Failure.VALIDATION,
                    "Order cannot exceed " + options.maxLines() + " lines."));
        }
        if (request.lines().stream().anyMatch(line -> line.quantity() <= 0 || line.unitPrice().signum() < 0)) {
            return completedFuture(fail(Failure.VALIDATION, "Invalid quantity or price."));
        }
        List<Line> lines = List.copyOf(request.lines());
        return invoke(() -> orders.find(request.idempotencyKey(), token)).thenCompose(existing -> {
            if (existing != null) {
                logger.log(System.Logger.Level.INFO, "Duplicate request " + request.idempotencyKey());
                return completedFuture(new Result(existing, Failure.NONE, "Duplicate request."));
            }
            return prepareOrder(request, lines, token);
        });
    }

    private CompletionStage<Result> prepareOrder(Request request, List<Line> lines, CancellationToken token) {
        Instant now = clock.instant();
        BigDecimal subtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return discounts.applicable(request.customer(), lines, now, token).thenCompose(applicable -> {
            BigDecimal discount = subtotal.multiply(discountPercent(applicable, request.customer(), now))
                    .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxable = subtotal.subtract(discount);
            BigDecimal taxAmount = tax.calculate(request.customer().countryCode(), taxable);
            Order order = new Order(UUID.randomUUID(), request.idempotencyKey(), request.customer().id(), null,
                    subtotal, discount, taxAmount, taxable.add(taxAmount), null, now);
            if (order.total().compareTo(options.manualReviewThreshold()) >= 0) {
                Order pending = order.complete(Status.PENDING_REVIEW, null);
                return persist(pending, token).thenApply(ignored ->
                        new Result(pending, Failure.NONE, "Order requires manual review."));
            }
            List<Line> reserved = new ArrayList<>();
            return reserveInventory(lines, reserved, token).thenCompose(unavailable -> {
                if (unavailable != null) {
                    return releaseAll(reserved).thenApply(ignored ->
                            fail(Failure.OUT_OF_STOCK, "SKU " + unavailable.sku() + " is out of stock."));
                }
                return chargeOrder(order, request, reserved, token);
            });
        });
    }

    private CompletionStage<Line> reserveInventory(List<Line> lines, List<Line> reserved,
                                                   CancellationToken token) {
        CompletionStage<Line> step = completedFuture(null);
        for (Line line : lines) {
            step = step.thenCompose(unavailable -> {
                if (unavailable != null) {
                    return completedFuture(unavailable);
                }
                return inventory.tryReserve(line.sku(), line.quantity(), token).thenApply(accepted -> {
                    if (!accepted) {
                        return line;
                    }
                    reserved.add(line);
                    return null;
                });
            });
        }
        return step;
    }

    private CompletionStage<Result> chargeOrder(Order order, Request request, List<Line> reserved,
                                                CancellationToken token) {
        PaymentRequest paymentRequest = new PaymentRequest(
                request.customer().id(), order.total(), request.paymentToken(), request.idempotencyKey());
        return charge(paymentRequest, token, 1).thenCompose(payment -> {
            if (payment == null || !payment.success()) {
                return releaseAll(reserved).thenApply(ignored -> {
                    if (payment != null && payment.transientFailure()) {
                        return fail(Failure.PAYMENT_UNAVAILABLE, "Payment provider unavailable.");
                    }
                    String message = payment == null ? null : payment.error();
                    return fail(Failure.PAYMENT_DECLINED, message == null ? "Payment declined." : message);
                });
            }
            Order confirmed = order.complete(Status.CONFIRMED, payment.transactionId());
            return persist(confirmed, token).exceptionallyCompose(failed -> {
                Throwable cause = unwrap(failed);
                logger.log(System.Logger.Level.ERROR, "Failed to save order " + confirmed.id(), cause);
                return payments.refund(payment.transactionId(), CancellationToken.NONE)
                        .thenCompose(ignored -> releaseAll(reserved))
                        .thenCompose(ignored -> failedFuture(cause));
            }).thenCompose(ignored -> notifyCustomer(confirmed, request.customer(), token));
        });
    }

    private CompletionStage<Payment> charge(PaymentRequest request, CancellationToken token, int attempt) {
        if (attempt > options.maxPaymentAttempts()) {
            return completedFuture(null);
        }
        return payments.charge(request, token).thenCompose(payment -> {
            if (payment.success() || !payment.transientFailure() || attempt == options.maxPaymentAttempts()) {
                return completedFuture(payment);
            }
            return delayer.delay(Duration.ofMillis(200L * attempt), token)
                    .thenCompose(ignored -> charge(request, token, attempt + 1));
        });
    }

    private CompletionStage<Void> persist(Order order, CancellationToken token) {
        return invoke(() -> unitOfWork.execute(() -> orders.save(order, token), token));
    }

    private CompletionStage<Result> notifyCustomer(Order order, Customer customer, CancellationToken token) {
        return invoke(() -> notifications.sendConfirmation(order, customer.email(), token))
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        if (cause instanceof CancellationException) {
                            throw new CompletionException(cause);
                        }
                        logger.log(System.Logger.Level.WARNING, "Confirmation failed for " + order.id(), cause);
                    }
                    return new Result(order, Failure.NONE, null);
                });
    }

    private CompletionStage<Void> releaseAll(List<Line> reserved) {
        CompletionStage<Void> step = completedFuture(null);
        for (Line line : reserved) {
            step = step.thenCompose(ignored -> invoke(() ->
                    inventory.release(line.sku(), line.quantity(), CancellationToken.NONE))
                    .handle((released, failure) -> {
                        if (failure != null) {
                            logger.log(System.Logger.Level.ERROR, "Failed to release " + line.sku(), unwrap(failure));
                        }
                        return null;
                    }));
        }
        return step;
    }

    private BigDecimal discountPercent(List<Discount> applicable, Customer customer, Instant now) {
        BigDecimal nonStackable = applicable.stream().filter(discount -> !discount.stackable())
                .map(Discount::percent).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal stackable = applicable.stream().filter(Discount::stackable)
                .map(Discount::percent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percent = nonStackable.max(stackable);
        if (customer.tier() == Tier.PLATINUM) {
            percent = percent.add(options.platinumExtraPercent());
        }
        var time = now.atZone(ZoneOffset.UTC);
        if ((time.getDayOfWeek() == DayOfWeek.SATURDAY || time.getDayOfWeek() == DayOfWeek.SUNDAY)
                && time.getHour() >= 17 && time.getHour() < 20) {
            percent = percent.add(BigDecimal.valueOf(2));
        }
        return percent.min(options.maxDiscountPercent());
    }

    private static <T> CompletionStage<T> invoke(Supplier<CompletionStage<T>> operation) {
        try {
            return operation.get();
        } catch (RuntimeException failure) {
            return failedFuture(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static Result fail(Failure reason, String message) {
        return new Result(null, reason, message);
    }
}
