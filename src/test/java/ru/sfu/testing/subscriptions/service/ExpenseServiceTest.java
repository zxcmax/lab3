package ru.sfu.testing.subscriptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sfu.testing.subscriptions.data.PaymentEntity;
import ru.sfu.testing.subscriptions.data.PaymentRepository;
import ru.sfu.testing.subscriptions.data.SubscriptionEntity;
import ru.sfu.testing.subscriptions.data.SubscriptionRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.sfu.testing.subscriptions.domain.Models.*;

class ExpenseServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final ExpenseService service = new ExpenseService(payments, subscriptions,
            Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void recordsPaymentOnCurrentUtcDate() {
        var input = new PaymentInput(SUBSCRIPTION, new BigDecimal("10.00"), TODAY);
        UUID paymentId = UUID.randomUUID();
        when(subscriptions.lockForChange(SUBSCRIPTION, OWNER)).thenReturn(Optional.of(subscription(Category.MEDIA)));
        when(payments.save(any(PaymentEntity.class))).thenAnswer(call -> {
            PaymentEntity saved = call.getArgument(0);
            assertEquals(OWNER, saved.getOwnerId());
            ReflectionTestUtils.setField(saved, "id", paymentId);
            return saved;
        });

        assertEquals(new Payment(paymentId, SUBSCRIPTION, "Example", Category.MEDIA,
                new BigDecimal("10.00"), TODAY), service.record(OWNER, input));
    }

    @Test
    void rejectsFuturePayment() {
        var input = new PaymentInput(SUBSCRIPTION, BigDecimal.ONE, TODAY.plusDays(1));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.record(OWNER, input)).getStatusCode());
        verifyNoInteractions(payments, subscriptions);
    }

    @Test
    void rejectsPaymentForUnavailableSubscription() {
        var input = new PaymentInput(SUBSCRIPTION, BigDecimal.ONE, TODAY);
        when(subscriptions.lockForChange(SUBSCRIPTION, OWNER)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.record(OWNER, input)).getStatusCode());
        verifyNoInteractions(payments);
    }

    @Test
    void sumsPaymentAmountsByCategoryAndExactlyInDecimal() {
        when(payments.findAllByOwnerIdAndPaidOnBetweenOrderByPaidOnAscIdAsc(OWNER, TODAY, TODAY))
                .thenReturn(List.of(payment("0.10", Category.MEDIA), payment("0.20", Category.MEDIA),
                        payment("10.00", Category.EDUCATION)));

        ExpenseReport report = service.summarize(OWNER, TODAY, TODAY);

        assertAll(
                () -> assertEquals("RUB", report.currency()),
                () -> assertEquals(new BigDecimal("10.30"), report.total()),
                () -> assertEquals(3, report.paymentCount()),
                () -> assertEquals(Map.of(Category.MEDIA, new BigDecimal("0.30"),
                        Category.EDUCATION, new BigDecimal("10.00")), report.byCategory()));
    }

    @Test
    void noPaymentsGivesZeroAndEmptyBreakdown() {
        when(payments.findAllByOwnerIdAndPaidOnBetweenOrderByPaidOnAscIdAsc(OWNER, TODAY.minusDays(365), TODAY))
                .thenReturn(List.of());

        ExpenseReport report = service.summarize(OWNER, TODAY.minusDays(365), TODAY);

        assertAll(
                () -> assertEquals(new BigDecimal("0.00"), report.total()),
                () -> assertEquals(0, report.paymentCount()),
                () -> assertTrue(report.byCategory().isEmpty()));
    }

    @Test
    void rejectsReversedAndOverlongDateRange() {
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        assertThrows(ApiException.class, () -> service.list(OWNER, TODAY, TODAY.minusDays(1)))
                                .getStatusCode()),
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        assertThrows(ApiException.class, () -> service.list(OWNER, TODAY.minusDays(366), TODAY))
                                .getStatusCode()));
        verifyNoInteractions(payments);
    }

    private static SubscriptionEntity subscription(Category category) {
        SubscriptionEntity entity = new SubscriptionEntity(OWNER, new SubscriptionInput("Example", category,
                BigDecimal.TEN, BillingPeriod.MONTHLY, TODAY, Status.ACTIVE));
        ReflectionTestUtils.setField(entity, "id", SUBSCRIPTION);
        return entity;
    }

    private static PaymentEntity payment(String amount, Category category) {
        return new PaymentEntity(subscription(category), new PaymentInput(SUBSCRIPTION, new BigDecimal(amount), TODAY));
    }
}
