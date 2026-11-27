package ru.sfu.testing.orders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import ru.sfu.testing.orders.OrderData.Customer;
import ru.sfu.testing.orders.OrderData.Discount;
import ru.sfu.testing.orders.OrderData.Line;
import ru.sfu.testing.orders.OrderData.Order;
import ru.sfu.testing.orders.OrderData.Payment;
import ru.sfu.testing.orders.OrderData.PaymentRequest;

public final class OrderDependencies {
    private OrderDependencies() {
    }

    @FunctionalInterface
    public interface CancellationToken {
        CancellationToken NONE = () -> false;

        boolean isCancellationRequested();

        default void throwIfCancellationRequested() {
            if (isCancellationRequested()) {
                throw new CancellationException();
            }
        }
    }

    public interface Repository {
        CompletionStage<Order> find(String idempotencyKey, CancellationToken token);
        CompletionStage<Void> save(Order order, CancellationToken token);
    }

    public interface Inventory {
        CompletionStage<Boolean> tryReserve(String sku, int quantity, CancellationToken token);
        CompletionStage<Void> release(String sku, int quantity, CancellationToken token);
    }

    public interface PaymentGateway {
        CompletionStage<Payment> charge(PaymentRequest request, CancellationToken token);
        CompletionStage<Void> refund(String transactionId, CancellationToken token);
    }

    @FunctionalInterface
    public interface DiscountEngine {
        CompletionStage<List<Discount>> applicable(Customer customer, List<Line> lines,
                                                    Instant now, CancellationToken token);
    }

    @FunctionalInterface
    public interface TaxCalculator {
        BigDecimal calculate(String countryCode, BigDecimal taxableAmount);
    }

    @FunctionalInterface
    public interface Notification {
        CompletionStage<Void> sendConfirmation(Order order, String email, CancellationToken token);
    }

    @FunctionalInterface
    public interface UnitOfWork {
        CompletionStage<Void> execute(Supplier<CompletionStage<Void>> operation, CancellationToken token);
    }

    @FunctionalInterface
    public interface Delayer {
        CompletionStage<Void> delay(Duration duration, CancellationToken token);
    }
}
