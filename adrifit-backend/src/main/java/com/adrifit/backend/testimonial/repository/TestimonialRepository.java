package com.adrifit.backend.testimonial.repository;

import com.adrifit.backend.testimonial.domain.Testimonial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    Optional<Testimonial> findByClientId(Long clientId);

    boolean existsByClientId(Long clientId);

    List<Testimonial> findByVisibleTrueOrderByCreatedAtDesc();

    List<Testimonial> findAllByOrderByCreatedAtDesc();
}
