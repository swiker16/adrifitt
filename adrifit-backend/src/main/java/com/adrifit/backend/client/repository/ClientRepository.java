package com.adrifit.backend.client.repository;

import com.adrifit.backend.client.domain.Client;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    long countByCreatedAtBetween(Instant from, Instant to);

    List<Client> findTop5ByOrderByCreatedAtDesc();
}
