package com.adrifit.backend.diet.repository;

import com.adrifit.backend.diet.domain.ClientDiet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDietRepository extends JpaRepository<ClientDiet, Long> {

    Optional<ClientDiet> findByClient_IdAndActiveTrue(Long clientId);

    List<ClientDiet> findAllByClient_IdOrderByAssignedAtDesc(Long clientId);

    boolean existsByDiet_IdAndActiveTrue(Long dietId);

    boolean existsByDiet_Id(Long dietId);
}
