package com.adrifit.backend.notification.repository;

import com.adrifit.backend.notification.domain.PushSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(Long userId);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    long countByUserId(Long userId);
}
