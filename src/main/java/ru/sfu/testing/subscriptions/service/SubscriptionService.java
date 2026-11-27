package ru.sfu.testing.subscriptions.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sfu.testing.subscriptions.data.SubscriptionEntity;
import ru.sfu.testing.subscriptions.data.SubscriptionRepository;
import ru.sfu.testing.subscriptions.domain.ApiException;

import java.util.List;
import java.util.UUID;

import static ru.sfu.testing.subscriptions.domain.Models.*;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptions;

    public SubscriptionService(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional
    public Subscription create(UUID owner, SubscriptionInput input) {
        return subscriptions.save(new SubscriptionEntity(owner, input)).toModel();
    }

    @Transactional(readOnly = true)
    public List<Subscription> list(UUID owner) {
        return subscriptions.findAllByOwnerIdAndDeletedFalseOrderByNextChargeDateAscIdAsc(owner)
                .stream().map(SubscriptionEntity::toModel).toList();
    }

    @Transactional(readOnly = true)
    public Subscription get(UUID owner, UUID id) {
        return subscriptions.findByIdAndOwnerIdAndDeletedFalse(id, owner)
                .orElseThrow(SubscriptionService::notFound).toModel();
    }

    @Transactional
    public Subscription update(UUID owner, UUID id, SubscriptionInput input) {
        SubscriptionEntity existing = subscriptions.lockForChange(id, owner)
                .orElseThrow(SubscriptionService::notFound);
        existing.update(input);
        return existing.toModel();
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        subscriptions.lockForChange(id, owner).orElseThrow(SubscriptionService::notFound).markDeleted();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Subscription not found");
    }
}
