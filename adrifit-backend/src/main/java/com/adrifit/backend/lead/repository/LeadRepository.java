package com.adrifit.backend.lead.repository;

import com.adrifit.backend.lead.domain.Lead;
import com.adrifit.backend.lead.domain.LeadStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findAllByOrderByCreatedAtDesc();

    List<Lead> findByStatusOrderByCreatedAtDesc(LeadStatus status);

    long countByStatus(LeadStatus status);

    Optional<Lead> findByQuestionnaireTokenHash(String questionnaireTokenHash);

    boolean existsByEmailIgnoreCaseAndStatusIn(String email, Collection<LeadStatus> statuses);

    Optional<Lead> findFirstByClientIdOrderByCreatedAtDesc(Long clientId);
}
