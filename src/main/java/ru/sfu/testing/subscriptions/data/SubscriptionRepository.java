package ru.sfu.testing.subscriptions.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    List<SubscriptionEntity> findAllByOwnerIdAndDeletedFalseOrderByNextChargeDateAscIdAsc(UUID ownerId);

    Optional<SubscriptionEntity> findByIdAndOwnerIdAndDeletedFalse(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT subscription FROM SubscriptionEntity subscription
            WHERE subscription.id = :id AND subscription.ownerId = :owner AND subscription.deleted = false
            """)
    Optional<SubscriptionEntity> lockForChange(@Param("id") UUID id, @Param("owner") UUID owner);
}
