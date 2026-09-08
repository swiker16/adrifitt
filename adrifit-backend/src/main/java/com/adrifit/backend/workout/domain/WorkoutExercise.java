package com.adrifit.backend.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workout_exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(name = "exercise_name", nullable = false)
    private String exerciseName;

    @Column(nullable = false)
    private Integer sets;

    @Column
    private Integer reps;

    private Integer rir;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(length = 1000)
    private String notes;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "day_number")
    private Integer dayNumber;

    @Column(name = "day_name", length = 100)
    private String dayName;

    @Column(name = "warm_up_sets", length = 50)
    private String warmUpSets;

    @Column(name = "approx_reps", length = 50)
    private String approxReps;
}
