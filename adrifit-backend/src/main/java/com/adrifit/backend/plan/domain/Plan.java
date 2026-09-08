package com.adrifit.backend.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "plans")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "monthly_price", nullable = false, precision = 8, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "review_frequency_days", nullable = false)
    private Integer reviewFrequencyDays;

    @Column(name = "messaging_enabled", nullable = false)
    private boolean messagingEnabled;

    @Column(name = "analytics_enabled", nullable = false)
    private boolean analyticsEnabled;

    @Column(name = "pdf_export_enabled", nullable = false)
    private boolean pdfExportEnabled;

    @Column(name = "priority_support", nullable = false)
    private boolean prioritySupport;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
