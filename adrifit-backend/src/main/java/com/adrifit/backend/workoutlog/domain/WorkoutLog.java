package com.adrifit.backend.workoutlog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One training session logged by the client: a day of its routine performed on a date, with the
 * load and RIR of every set. Workout/exercise names are snapshotted so history survives routine
 * changes.
 */
@Entity
@Table(name = "workout_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_workout_log_day",
                columnNames = {"client_id", "performed_on", "day_number"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "workout_id")
    private Long workoutId;

    @Column(name = "workout_name", nullable = false, length = 255)
    private String workoutName;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "day_name", length = 100)
    private String dayName;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    @OneToMany(mappedBy = "log", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("exerciseOrder ASC, setNumber ASC")
    private List<WorkoutLogSet> sets = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
