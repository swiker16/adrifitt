package com.adrifit.backend.diet.repository;

import com.adrifit.backend.diet.domain.Diet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DietRepository extends JpaRepository<Diet, Long> {

    List<Diet> findAllByOrderByCreatedAtDesc();

    long countByIdIn(List<Long> ids);
}
