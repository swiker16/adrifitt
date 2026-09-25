package com.adrifit.backend.testimonial.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Review published by a client on the public landing page. Only clients can write them and only
 * once (unique client_id); they cannot be edited afterwards.
 */
@Entity
@Table(name = "testimonials")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true)
    private Long clientId;

    /** Public display name, e.g. "Laura M.". */
    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 1000)
    private String content;

    /** The trainer can hide an inappropriate review from the landing (it cannot edit it). */
    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
