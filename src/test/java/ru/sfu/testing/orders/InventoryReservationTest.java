package ru.sfu.testing.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.sfu.testing.orders.OrderData.*;
import static ru.sfu.testing.orders.OrderDependencies.*;

@Timeout(10)
class InventoryReservationTest {
    private static final Customer CUSTOMER = new Customer(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "customer@example.test",
            Tier.STANDARD, "FI");

    private final CancellationToken token = () -> false;
    private final Repository orders = mock(Repository.class);
    private final Inventory inventory = mock(Inventory.class);
    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final Notification notifications = mock(Notification.class);
    private final System.Logger logger = mock(System.Logger.class);
    private OrderProcessingService service;

    @BeforeEach
    void setUp() {
        when(orders.find("order-1", token)).thenReturn(completedFuture(null));
        when(inventory.tryReserve(anyString(), anyInt(), same(token))).thenReturn(completedFuture(true));
        when(inventory.release(anyString(), anyInt(), same(CancellationToken.NONE)))
                .thenReturn(completedFuture(null));
        service = new OrderProcessingService(orders, inventory, payments,
                (customer, lines, now, cancellation) -> completedFuture(List.of()),
                (country, taxable) -> BigDecimal.ZERO, notifications, (work, cancellation) -> work.get(),
                Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC),
                (duration, cancellation) -> completedFuture(null), Options.DEFAULT, logger);
    }

    @Test
    void waitsForEveryReservationBeforeChargingAndSaving() {
        var first = new CompletableFuture<Boolean>();
        var second = new CompletableFuture<Boolean>();
        when(inventory.tryReserve("A", 1, token)).thenReturn(first);
        when(inventory.tryReserve("B", 2, token)).thenReturn(second);
        when(payments.charge(any(), same(token)))
                .thenReturn(completedFuture(new Payment(true, false, "tx-1", null)));
        when(orders.save(any(), same(token))).thenReturn(completedFuture(null));
        when(notifications.sendConfirmation(any(), eq(CUSTOMER.email()), same(token)))
                .thenReturn(completedFuture(null));

        var result = service.placeOrderAsync(request(line("A", 1), line("B", 2)), token).toCompletableFuture();
        verify(inventory, never()).tryReserve("B", 2, token);
        verifyNoInteractions(payments);

        first.complete(true);
        verify(inventory).tryReserve("B", 2, token);
        assertFalse(result.isDone());
        verifyNoInteractions(payments);

        second.complete(true);
        assertEquals(Status.CONFIRMED, result.join().order().status());

        InOrder flow = inOrder(inventory, payments, orders, notifications);
        flow.verify(inventory).tryReserve("A", 1, token);
        flow.verify(inventory).tryReserve("B", 2, token);
        flow.verify(payments).charge(any(), same(token));
        flow.verify(orders).save(any(), same(token));
        flow.verify(notifications).sendConfirmation(any(), eq(CUSTOMER.email()), same(token));
        verify(inventory, never()).release(anyString(), anyInt(), any());
    }

    @Test
    void firstRejectionSkipsOtherLinesWithoutReleasing() {
        when(inventory.tryReserve("A", 1, token)).thenReturn(completedFuture(false));

        Result result = place(line("A", 1), line("B", 2));

        assertOutOfStock(result, "A");
        verify(inventory).tryReserve("A", 1, token);
        verifyNoMoreInteractions(inventory);
        verifyNoDownstreamEffects();
    }

    @Test
    void laterRejectionRollsBackOnlyAcceptedLinesInOrder() {
        when(inventory.tryReserve("C", 3, token)).thenReturn(completedFuture(false));

        Result result = place(line("A", 1), line("B", 2), line("C", 3), line("D", 4));

        assertOutOfStock(result, "C");
        InOrder flow = inOrder(inventory);
        flow.verify(inventory).tryReserve("A", 1, token);
        flow.verify(inventory).tryReserve("B", 2, token);
        flow.verify(inventory).tryReserve("C", 3, token);
        flow.verify(inventory).release("A", 1, CancellationToken.NONE);
        flow.verify(inventory).release("B", 2, CancellationToken.NONE);
        verifyNoMoreInteractions(inventory);
        verifyNoDownstreamEffects();
    }

    @Test
    void outOfStockResultWaitsForRollback() {
        var pendingRelease = new CompletableFuture<Void>();
        when(inventory.tryReserve("B", 2, token)).thenReturn(completedFuture(false));
        when(inventory.release("A", 1, CancellationToken.NONE)).thenReturn(pendingRelease);

        var result = service.placeOrderAsync(request(line("A", 1), line("B", 2)), token).toCompletableFuture();
        assertFalse(result.isDone());
        verify(inventory).release("A", 1, CancellationToken.NONE);
        pendingRelease.complete(null);

        assertOutOfStock(result.join(), "B");
        verifyNoDownstreamEffects();
    }

    @Test
    void rollbackContinuesWhenAReleaseFails() {
        IllegalStateException failure = new IllegalStateException("Inventory unavailable");
        when(inventory.tryReserve("C", 3, token)).thenReturn(completedFuture(false));
        when(inventory.release("A", 1, CancellationToken.NONE)).thenReturn(failedFuture(failure));

        Result result = place(line("A", 1), line("B", 2), line("C", 3));

        assertOutOfStock(result, "C");
        verify(inventory).release("A", 1, CancellationToken.NONE);
        verify(inventory).release("B", 2, CancellationToken.NONE);
        verify(logger).log(System.Logger.Level.ERROR, "Failed to release A", failure);
        verifyNoDownstreamEffects();
    }

    @Test
    void duplicateSkuRollsBackEachAcceptedQuantity() {
        when(inventory.tryReserve("B", 3, token)).thenReturn(completedFuture(false));

        Result result = place(line("A", 1), line("A", 2), line("B", 3));

        assertOutOfStock(result, "B");
        InOrder flow = inOrder(inventory);
        flow.verify(inventory).tryReserve("A", 1, token);
        flow.verify(inventory).tryReserve("A", 2, token);
        flow.verify(inventory).tryReserve("B", 3, token);
        flow.verify(inventory).release("A", 1, CancellationToken.NONE);
        flow.verify(inventory).release("A", 2, CancellationToken.NONE);
        verifyNoMoreInteractions(inventory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedOrCancelledReservationPropagatesWithoutRollbackAsInOriginal(boolean cancelled) {
        RuntimeException failure = cancelled
                ? new CancellationException("Reservation cancelled")
                : new IllegalStateException("Inventory unavailable");
        when(inventory.tryReserve("B", 2, token)).thenReturn(failedFuture(failure));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> place(line("A", 1), line("B", 2), line("C", 3)));

        assertSame(failure, thrown.getCause());
        verify(inventory).tryReserve("A", 1, token);
        verify(inventory).tryReserve("B", 2, token);
        verifyNoMoreInteractions(inventory);
        verifyNoDownstreamEffects();
    }

    private Result place(Line... lines) {
        return service.placeOrderAsync(request(lines), token).toCompletableFuture().join();
    }

    private static Request request(Line... lines) {
        return new Request("order-1", CUSTOMER, List.of(lines), "token");
    }

    private static Line line(String sku, int quantity) {
        return new Line(sku, quantity, BigDecimal.TEN);
    }

    private static void assertOutOfStock(Result result, String sku) {
        assertAll(
                () -> assertFalse(result.isSuccess()),
                () -> assertEquals(Failure.OUT_OF_STOCK, result.failure()),
                () -> assertNull(result.order()),
                () -> assertEquals("SKU " + sku + " is out of stock.", result.message()));
    }

    private void verifyNoDownstreamEffects() {
        verifyNoInteractions(payments, notifications);
        verify(orders, never()).save(any(), any());
    }
}
