package ru.sfu.testing.primes;

public final class TrialDivisionPrimeService implements PrimeService {
    @Override
    public boolean isPrime(int candidate) {
        if (candidate <= 0) {
            throw new IllegalArgumentException("Candidate must be positive");
        }
        if (candidate <= 2) {
            return candidate == 2;
        }
        if (candidate % 2 == 0) {
            return false;
        }
        for (int divisor = 3; divisor <= candidate / divisor; divisor += 2) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void clearCache() {
        // Trial division does not cache results.
    }
}
