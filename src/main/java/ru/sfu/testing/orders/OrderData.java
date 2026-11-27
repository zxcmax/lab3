package ru.sfu.testing.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderData {
    private OrderData() {
    }

    public enum Status { CONFIRMED, PENDING_REVIEW }
    public enum Failure { NONE, VALIDATION, OUT_OF_STOCK, PAYMENT_DECLINED, PAYMENT_UNAVAILABLE }
    public enum Tier { STANDARD, GOLD, PLATINUM }

    public record Customer(UUID id, String email, Tier tier, String countryCode) {}
    public record Line(String sku, int quantity, BigDecimal unitPrice) {}
    public record Discount(String code, BigDecimal percent, boolean stackable) {}
    public record Request(String idempotencyKey, Customer customer, List<Line> lines, String paymentToken) {}
    public record PaymentRequest(UUID customerId, BigDecimal amount, String token, String idempotencyKey) {}
    public record Payment(boolean success, boolean transientFailure, String transactionId, String error) {}

    public record Order(UUID id, String idempotencyKey, UUID customerId, Status status,
                        BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total,
                        String transactionId, Instant createdAt) {
        Order complete(Status newStatus, String transaction) {
            return new Order(id, idempotencyKey, customerId, newStatus, subtotal,
                    discount, tax, total, transaction, createdAt);
        }
    }

    public record Result(Order order, Failure failure, String message) {
        public boolean isSuccess() {
            return failure == Failure.NONE;
        }
    }

    public record Options(int maxLines, BigDecimal manualReviewThreshold, BigDecimal maxDiscountPercent,
                          int maxPaymentAttempts, BigDecimal platinumExtraPercent) {
        public static final Options DEFAULT = new Options(
                50, BigDecimal.valueOf(10_000), BigDecimal.valueOf(50), 3, BigDecimal.valueOf(5));
    }
}
