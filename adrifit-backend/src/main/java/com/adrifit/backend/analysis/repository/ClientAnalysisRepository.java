package com.adrifit.backend.analysis.repository;

import com.adrifit.backend.analysis.domain.AnalysisStatus;
import com.adrifit.backend.analysis.domain.ClientAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientAnalysisRepository extends JpaRepository<ClientAnalysis, Long> {

    List<ClientAnalysis> findByClientIdAndActiveTrueOrderByUploadedAtDesc(Long clientId);

    Optional<ClientAnalysis> findByIdAndClientIdAndActiveTrue(Long id, Long clientId);

    long countByStatusAndActiveTrue(AnalysisStatus status);
}
