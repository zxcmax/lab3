package ru.sfu.testing.primes;

public interface PrimeService {
    boolean isPrime(int candidate);

    void clearCache();
}
