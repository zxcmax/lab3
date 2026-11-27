package ru.sfu.testing.subscriptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sfu.testing.subscriptions.data.SubscriptionEntity;
import ru.sfu.testing.subscriptions.data.SubscriptionRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.sfu.testing.subscriptions.domain.Models.*;

class SubscriptionServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate CHARGE = LocalDate.of(2026, 12, 1);
    private static final SubscriptionInput INPUT = new SubscriptionInput(" Example ", Category.SOFTWARE,
            BigDecimal.TEN, BillingPeriod.MONTHLY, CHARGE, Status.ACTIVE);
    private final SubscriptionRepository repository = mock(SubscriptionRepository.class);
    private final SubscriptionService service = new SubscriptionService(repository);

    @Test
    void createNormalizesNameAndDecimalScale() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(SubscriptionEntity.class))).thenAnswer(call -> {
            SubscriptionEntity entry = call.getArgument(0);
            assertEquals(OWNER, entry.getOwnerId());
            ReflectionTestUtils.setField(entry, "id", id);
            return entry;
        });

        Subscription created = service.create(OWNER, INPUT);

        assertEquals(new Subscription(id, "Example", Category.SOFTWARE, new BigDecimal("10.00"),
                BillingPeriod.MONTHLY, CHARGE, Status.ACTIVE), created);
    }

    @Test
    void listAndGetUseOwnerScope() {
        UUID id = UUID.randomUUID();
        SubscriptionEntity entry = storedSubscription(id);
        when(repository.findAllByOwnerIdAndDeletedFalseOrderByNextChargeDateAscIdAsc(OWNER))
                .thenReturn(List.of(entry));
        when(repository.findByIdAndOwnerIdAndDeletedFalse(id, OWNER)).thenReturn(Optional.of(entry));

        assertAll(
                () -> assertEquals(List.of(entry.toModel()), service.list(OWNER)),
                () -> assertEquals(entry.toModel(), service.get(OWNER, id)));
    }

    @Test
    void updateRetainsIdentifierAndAppliesNewStatus() {
        UUID id = UUID.randomUUID();
        var updated = new SubscriptionInput("Example", Category.SOFTWARE, BigDecimal.TEN,
                BillingPeriod.YEARLY, CHARGE, Status.PAUSED);
        when(repository.lockForChange(id, OWNER)).thenReturn(Optional.of(storedSubscription(id)));

        Subscription result = service.update(OWNER, id, updated);

        assertAll(() -> assertEquals(id, result.id()), () -> assertEquals(Status.PAUSED, result.status()));
    }

    @Test
    void deleteUsesOwnerAndMarksStoredSubscriptionDeleted() {
        UUID id = UUID.randomUUID();
        SubscriptionEntity entry = storedSubscription(id);
        when(repository.lockForChange(id, OWNER)).thenReturn(Optional.of(entry));

        service.delete(OWNER, id);

        assertEquals(true, ReflectionTestUtils.getField(entry, "deleted"));
    }

    @Test
    void absentOrForeignRecordsHaveSameNotFoundResponse() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOwnerIdAndDeletedFalse(id, OWNER)).thenReturn(Optional.empty());
        when(repository.lockForChange(id, OWNER)).thenReturn(Optional.empty());

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        assertThrows(ApiException.class, () -> service.get(OWNER, id)).getStatusCode()),
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        assertThrows(ApiException.class, () -> service.update(OWNER, id, INPUT)).getStatusCode()),
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        assertThrows(ApiException.class, () -> service.delete(OWNER, id)).getStatusCode()));
    }

    private static SubscriptionEntity storedSubscription(UUID id) {
        SubscriptionEntity entity = new SubscriptionEntity(OWNER, INPUT);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
