package com.adrifit.backend.video.repository;

import com.adrifit.backend.video.domain.TechniqueVideo;
import com.adrifit.backend.video.domain.VideoSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechniqueVideoRepository extends JpaRepository<TechniqueVideo, Long> {

    List<TechniqueVideo> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<TechniqueVideo> findAllByOrderByCreatedAtDesc();

    List<TechniqueVideo> findBySourceAndReviewedAtIsNullOrderByCreatedAtAsc(VideoSource source);

    long countBySourceAndReviewedAtIsNull(VideoSource source);

    /** Trainer videos not yet seen by the client + corrections not yet read. */
    @Query("""
            select count(v) from TechniqueVideo v
            where v.clientId = :clientId and v.clientSeenAt is null
              and (v.source = com.adrifit.backend.video.domain.VideoSource.TRAINER or v.reviewedAt is not null)
            """)
    long countUnseenByClient(@Param("clientId") Long clientId);
}
