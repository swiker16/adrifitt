package com.adrifit.backend.photo.repository;

import com.adrifit.backend.photo.domain.ProgressPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressPhotoRepository extends JpaRepository<ProgressPhoto, Long> {

    List<ProgressPhoto> findByClientIdOrderByTakenOnDescIdDesc(Long clientId);

    long countByClientId(Long clientId);
}
