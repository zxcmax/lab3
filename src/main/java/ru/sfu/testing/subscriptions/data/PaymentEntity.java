package ru.sfu.testing.subscriptions.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "subscription_name", nullable = false, length = 120, updatable = false)
    private String subscriptionName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, updatable = false)
    private Category category;

    @Column(nullable = false, precision = 11, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    protected PaymentEntity() {
    }

    public PaymentEntity(SubscriptionEntity subscription, PaymentInput input) {
        Subscription snapshot = subscription.toModel();
        ownerId = subscription.getOwnerId();
        subscriptionId = snapshot.id();
        subscriptionName = snapshot.name();
        category = snapshot.category();
        amount = input.amount().setScale(2);
        paidOn = input.paidOn();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Payment toModel() {
        return new Payment(id, subscriptionId, subscriptionName, category, amount, paidOn);
    }
}
