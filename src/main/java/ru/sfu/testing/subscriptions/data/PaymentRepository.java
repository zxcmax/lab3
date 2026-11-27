package ru.sfu.testing.subscriptions.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findAllByOwnerIdAndPaidOnBetweenOrderByPaidOnAscIdAsc(
            UUID ownerId, LocalDate from, LocalDate to);
}
