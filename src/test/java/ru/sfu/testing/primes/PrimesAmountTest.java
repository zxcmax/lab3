package ru.sfu.testing.primes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@Timeout(10)
class PrimesAmountTest {
    @Test
    void returnsPrimesInHalfOpenRangeWithoutAssumingOrder() {
        PrimeService service = mock(PrimeService.class);
        when(service.isPrime(anyInt())).thenAnswer(call ->
                List.of(2, 3, 5, 7).contains(call.<Integer>getArgument(0)));
        PrimesAmount counter = new PrimesAmount(11);

        var result = counter.countPrimesAmount(service);

        assertAll(
                () -> assertEquals(List.of(2, 3, 5, 7), result.stream().sorted().toList()),
                () -> assertEquals(4, counter.count()),
                () -> assertEquals(11, counter.border()));
        for (int candidate = 2; candidate < 11; candidate++) {
            verify(service).isPrime(candidate);
        }
        verify(service).clearCache();
        verifyNoMoreInteractions(service);
    }

    @Test
    void waitsForPendingCheckBeforeReturningAndClearingCache() throws Exception {
        PrimeService service = mock(PrimeService.class);
        CountDownLatch checkStarted = new CountDownLatch(1);
        CountDownLatch allowCheckToFinish = new CountDownLatch(1);
        when(service.isPrime(3)).thenAnswer(call -> {
            checkStarted.countDown();
            if (!allowCheckToFinish.await(4, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the pending check");
            }
            return true;
        });
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> pending = caller.submit(() ->
                    List.copyOf(new PrimesAmount(5).countPrimesAmount(service)));
            assertTrue(checkStarted.await(3, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            verify(service, never()).clearCache();

            allowCheckToFinish.countDown();

            assertEquals(List.of(3), pending.get(3, TimeUnit.SECONDS));
            verify(service).clearCache();
        } finally {
            allowCheckToFinish.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    void clearsPartialResultsAndCacheAfterCheckFails() {
        PrimeService service = mock(PrimeService.class);
        when(service.isPrime(2)).thenReturn(true);
        when(service.isPrime(3)).thenThrow(new IllegalStateException("Check failed"));
        PrimesAmount counter = new PrimesAmount(5);

        var result = counter.countPrimesAmount(service);

        assertAll(() -> assertTrue(result.isEmpty()), () -> assertEquals(0, counter.count()));
        verify(service).isPrime(2);
        verify(service).isPrime(3);
        verify(service).isPrime(4);
        verify(service).clearCache();
    }

    @Test
    void borderOneDoesNotCallService() {
        PrimeService service = mock(PrimeService.class);
        PrimesAmount counter = new PrimesAmount(1);

        assertTrue(counter.countPrimesAmount(service).isEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void borderTwoDoesNotCheckAnyCandidate() {
        PrimeService service = mock(PrimeService.class);

        assertTrue(new PrimesAmount(2).countPrimesAmount(service).isEmpty());

        verify(service).clearCache();
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsNonPositiveBorder(int border) {
        assertThrows(IllegalArgumentException.class, () -> new PrimesAmount(border));
    }

    @Test
    void countIsUnavailableUntilFirstRun() {
        assertThrows(IllegalStateException.class, () -> new PrimesAmount(5).count());
    }

    @Test
    void concreteServiceIncludesTwo() {
        var result = new PrimesAmount(11).countPrimesAmount(new TrialDivisionPrimeService());

        assertEquals(List.of(2, 3, 5, 7), result.stream().sorted().toList());
    }
}
