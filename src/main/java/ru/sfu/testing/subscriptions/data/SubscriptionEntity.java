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
@Table(name = "subscriptions")
public class SubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "billing_period", nullable = false, length = 10)
    private BillingPeriod billingPeriod;

    @Column(name = "next_charge_date", nullable = false)
    private LocalDate nextChargeDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(nullable = false)
    private boolean deleted;

    protected SubscriptionEntity() {
    }

    public SubscriptionEntity(UUID ownerId, SubscriptionInput input) {
        this.ownerId = ownerId;
        update(input);
    }

    public void update(SubscriptionInput input) {
        name = input.name().strip();
        category = input.category();
        amount = input.amount().setScale(2);
        billingPeriod = input.billingPeriod();
        nextChargeDate = input.nextChargeDate();
        status = input.status();
    }

    public void markDeleted() {
        deleted = true;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Subscription toModel() {
        return new Subscription(id, name, category, amount, billingPeriod, nextChargeDate, status);
    }
}
