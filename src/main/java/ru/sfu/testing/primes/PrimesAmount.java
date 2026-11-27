package ru.sfu.testing.primes;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public final class PrimesAmount {
    private final int border;
    private volatile Integer count;

    public PrimesAmount(int border) {
        if (border <= 0) {
            throw new IllegalArgumentException("Border must be positive");
        }
        this.border = border;
    }

    public int border() {
        return border;
    }

    public int count() {
        Integer lastCount = count;
        if (lastCount == null) {
            throw new IllegalStateException("Count is not set");
        }
        return lastCount;
    }

    /** Checks the interval [2, border) in parallel. Queue order is unspecified. */
    public synchronized ConcurrentLinkedQueue<Integer> countPrimesAmount(PrimeService service) {
        Objects.requireNonNull(service, "service");
        ConcurrentLinkedQueue<Integer> primes = new ConcurrentLinkedQueue<>();
        if (border > 1) {
            AtomicBoolean failed = new AtomicBoolean();
            try {
                IntStream.range(2, border).parallel().forEach(candidate -> {
                    try {
                        if (service.isPrime(candidate)) {
                            primes.add(candidate);
                        }
                    } catch (RuntimeException failure) {
                        failed.set(true);
                    }
                });
                if (failed.get()) {
                    primes.clear();
                }
            } finally {
                service.clearCache();
            }
        }
        count = primes.size();
        return primes;
    }
}
