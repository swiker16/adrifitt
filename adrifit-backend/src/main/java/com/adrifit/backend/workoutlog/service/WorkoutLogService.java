package com.adrifit.backend.workoutlog.service;

import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.domain.Workout;
import com.adrifit.backend.workout.domain.WorkoutExercise;
import com.adrifit.backend.workout.repository.ClientWorkoutRepository;
import com.adrifit.backend.workoutlog.domain.WorkoutLog;
import com.adrifit.backend.workoutlog.domain.WorkoutLogSet;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.ExerciseProgressPoint;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.ExerciseProgressResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.LogSetRequest;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.LogTemplateResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.LoggedExercise;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.SaveWorkoutLogRequest;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.SetValue;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.TemplateExercise;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.WorkoutDayResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.WorkoutLogResponse;
import com.adrifit.backend.workoutlog.repository.WorkoutLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workout log: the client picks a day of its active routine and records, for every set that the
 * routine prescribes, the load (kg) and RIR achieved. Exercises without a day number belong to day 1.
 */
@Service
@Transactional(readOnly = true)
public class WorkoutLogService {

    private static final String DEFAULT_DAY_NAME = "Entrenamiento";

    private final WorkoutLogRepository repository;
    private final ClientWorkoutRepository clientWorkoutRepository;
    private final ClientService clientService;

    public WorkoutLogService(WorkoutLogRepository repository,
                             ClientWorkoutRepository clientWorkoutRepository,
                             ClientService clientService) {
        this.repository = repository;
        this.clientWorkoutRepository = clientWorkoutRepository;
        this.clientService = clientService;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    public List<WorkoutDayResponse> getMyDays() {
        Workout workout = activeWorkout(clientService.getCurrentClientId());
        Map<Integer, List<WorkoutExercise>> byDay = groupByDay(workout);
        return byDay.entrySet().stream()
                .map(e -> new WorkoutDayResponse(e.getKey(), dayName(e.getValue(), e.getKey()), e.getValue().size(),
                        e.getValue().stream().mapToInt(WorkoutExercise::getSets).sum()))
                .toList();
    }

    public LogTemplateResponse getMyTemplate(Integer dayNumber) {
        Long clientId = clientService.getCurrentClientId();
        Workout workout = activeWorkout(clientId);
        List<WorkoutExercise> exercises = exercisesOfDay(workout, dayNumber);

        // Previous values: last log of the same day, matched by exercise id (or name as fallback).
        Map<String, List<SetValue>> previous = new LinkedHashMap<>();
        repository.findFirstByClientIdAndDayNumberOrderByPerformedOnDescIdDesc(clientId, dayNumber).ifPresent(last -> {
            for (WorkoutLogSet s : last.getSets()) {
                previous.computeIfAbsent(key(s.getExerciseId(), s.getExerciseName()), k -> new ArrayList<>())
                        .add(new SetValue(s.getSetNumber(), s.getWeightKg(), s.getReps(), s.getRir()));
                previous.computeIfAbsent(nameKey(s.getExerciseName()), k -> new ArrayList<>())
                        .add(new SetValue(s.getSetNumber(), s.getWeightKg(), s.getReps(), s.getRir()));
            }
        });

        List<TemplateExercise> items = exercises.stream()
                .map(e -> new TemplateExercise(e.getId(), e.getExerciseName(), e.getSets(), e.getReps(), e.getApproxReps(),
                        e.getRir(), e.getRestSeconds(), e.getNotes(),
                        previous.getOrDefault(key(e.getId(), e.getExerciseName()),
                                previous.getOrDefault(nameKey(e.getExerciseName()), List.of()))))
                .toList();
        return new LogTemplateResponse(workout.getId(), workout.getName(), dayNumber,
                dayName(exercises, dayNumber), items);
    }

    @Transactional
    public WorkoutLogResponse createMine(SaveWorkoutLogRequest request) {
        Long clientId = clientService.getCurrentClientId();
        validateDate(request.performedOn());
        if (repository.findByClientIdAndPerformedOnAndDayNumber(clientId, request.performedOn(), request.dayNumber()).isPresent()) {
            throw new BusinessException("Ya registraste ese día de la rutina en esa fecha. Edita el registro existente.");
        }
        Workout workout = activeWorkout(clientId);
        List<WorkoutExercise> exercises = exercisesOfDay(workout, request.dayNumber());

        WorkoutLog log = WorkoutLog.builder()
                .clientId(clientId)
                .workoutId(workout.getId())
                .workoutName(workout.getName())
                .dayNumber(request.dayNumber())
                .dayName(dayName(exercises, request.dayNumber()))
                .performedOn(request.performedOn())
                .notes(blankToNull(request.notes()))
                .build();
        applySets(log, exercises, request.sets());
        return toResponse(repository.save(log));
    }

    @Transactional
    public WorkoutLogResponse updateMine(Long id, SaveWorkoutLogRequest request) {
        Long clientId = clientService.getCurrentClientId();
        WorkoutLog log = getOwned(id, clientId);
        validateDate(request.performedOn());
        if (!log.getDayNumber().equals(request.dayNumber())) {
            throw new BusinessException("No se puede cambiar el día de un registro. Crea uno nuevo.");
        }
        repository.findByClientIdAndPerformedOnAndDayNumber(clientId, request.performedOn(), request.dayNumber())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException("Ya existe un registro de ese día en esa fecha");
                });

        Workout workout = activeWorkout(clientId);
        if (!Objects.equals(workout.getId(), log.getWorkoutId())) {
            throw new BusinessException("Este registro pertenece a una rutina anterior y ya no se puede editar");
        }
        List<WorkoutExercise> exercises = exercisesOfDay(workout, request.dayNumber());
        log.setPerformedOn(request.performedOn());
        log.setNotes(blankToNull(request.notes()));
        log.getSets().clear();
        repository.flush();
        applySets(log, exercises, request.sets());
        return toResponse(repository.save(log));
    }

    @Transactional
    public void deleteMine(Long id) {
        repository.delete(getOwned(id, clientService.getCurrentClientId()));
    }

    public List<WorkoutLogResponse> findMine() {
        return findForClient(clientService.getCurrentClientId());
    }

    public WorkoutLogResponse findMineById(Long id) {
        return toResponse(getOwned(id, clientService.getCurrentClientId()));
    }

    public List<ExerciseProgressResponse> myProgress() {
        return progressForClient(clientService.getCurrentClientId());
    }

    // ── Trainer / shared ────────────────────────────────────────────────────

    public List<WorkoutLogResponse> findForClient(Long clientId) {
        clientService.assertCanAccess(clientId);
        return repository.findByClientIdOrderByPerformedOnDescIdDesc(clientId).stream().map(this::toResponse).toList();
    }

    /**
     * Per exercise (by name) and per session: heaviest set, total volume, best estimated 1RM
     * (Epley: w × (1 + reps/30)) and lowest RIR.
     */
    public List<ExerciseProgressResponse> progressForClient(Long clientId) {
        clientService.assertCanAccess(clientId);
        Map<String, TreeMap<LocalDate, List<WorkoutLogSet>>> byExercise = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (WorkoutLog log : repository.findByClientIdOrderByPerformedOnAscIdAsc(clientId)) {
            for (WorkoutLogSet s : log.getSets()) {
                byExercise.computeIfAbsent(s.getExerciseName().trim(), k -> new TreeMap<>())
                        .computeIfAbsent(log.getPerformedOn(), k -> new ArrayList<>())
                        .add(s);
            }
        }
        List<ExerciseProgressResponse> result = new ArrayList<>();
        byExercise.forEach((name, byDate) -> {
            List<ExerciseProgressPoint> points = new ArrayList<>();
            byDate.forEach((date, sets) -> {
                BigDecimal max = sets.stream().map(WorkoutLogSet::getWeightKg).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                BigDecimal volume = sets.stream()
                        .filter(s -> s.getReps() != null)
                        .map(s -> s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal e1rm = sets.stream()
                        .filter(s -> s.getReps() != null && s.getReps() > 0)
                        .map(s -> s.getWeightKg().multiply(BigDecimal.ONE.add(
                                BigDecimal.valueOf(s.getReps()).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP))))
                        .max(Comparator.naturalOrder())
                        .map(v -> v.setScale(1, RoundingMode.HALF_UP))
                        .orElse(null);
                Integer bestRir = sets.stream().map(WorkoutLogSet::getRir).filter(Objects::nonNull).min(Integer::compare).orElse(null);
                points.add(new ExerciseProgressPoint(date, max, volume.setScale(1, RoundingMode.HALF_UP), e1rm, bestRir));
            });
            result.add(new ExerciseProgressResponse(name, points));
        });
        return result;
    }

    public long countInRange(Long clientId, LocalDate from, LocalDate to) {
        return repository.countByClientIdAndPerformedOnBetween(clientId, from, to);
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        repository.deleteAll(repository.findByClientIdOrderByPerformedOnDescIdDesc(event.clientId()));
    }

    // ── internals ───────────────────────────────────────────────────────────

    private void applySets(WorkoutLog log, List<WorkoutExercise> dayExercises, List<LogSetRequest> sets) {
        Map<Long, WorkoutExercise> byId = dayExercises.stream()
                .collect(Collectors.toMap(WorkoutExercise::getId, e -> e));
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < dayExercises.size(); i++) {
            order.put(dayExercises.get(i).getId(), i);
        }
        Set<String> seen = new HashSet<>();
        for (LogSetRequest s : sets) {
            WorkoutExercise exercise = byId.get(s.exerciseId());
            if (exercise == null) {
                throw new BusinessException("El ejercicio " + s.exerciseId() + " no pertenece a este día de la rutina");
            }
            if (s.setNumber() > exercise.getSets()) {
                throw new BusinessException(exercise.getExerciseName() + " solo tiene " + exercise.getSets() + " series");
            }
            if (!seen.add(s.exerciseId() + ":" + s.setNumber())) {
                throw new BusinessException("Serie duplicada en " + exercise.getExerciseName());
            }
            log.getSets().add(WorkoutLogSet.builder()
                    .log(log)
                    .exerciseId(exercise.getId())
                    .exerciseName(exercise.getExerciseName())
                    .exerciseOrder(order.get(exercise.getId()))
                    .setNumber(s.setNumber())
                    .weightKg(s.weightKg())
                    .reps(s.reps())
                    .rir(s.rir())
                    .build());
        }
    }

    private Workout activeWorkout(Long clientId) {
        return clientWorkoutRepository.findByClientIdAndActiveTrue(clientId)
                .map(ClientWorkout::getWorkout)
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no tienes una rutina asignada"));
    }

    private Map<Integer, List<WorkoutExercise>> groupByDay(Workout workout) {
        Map<Integer, List<WorkoutExercise>> byDay = new TreeMap<>();
        for (WorkoutExercise e : workout.getExercises()) {
            byDay.computeIfAbsent(e.getDayNumber() != null ? e.getDayNumber() : 1, k -> new ArrayList<>()).add(e);
        }
        return byDay;
    }

    private List<WorkoutExercise> exercisesOfDay(Workout workout, Integer dayNumber) {
        List<WorkoutExercise> exercises = groupByDay(workout).get(dayNumber);
        if (exercises == null || exercises.isEmpty()) {
            throw new ResourceNotFoundException("La rutina no tiene el día " + dayNumber);
        }
        return exercises;
    }

    private String dayName(List<WorkoutExercise> exercises, Integer dayNumber) {
        return exercises.stream().map(WorkoutExercise::getDayName).filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(exercises.stream().anyMatch(e -> e.getDayNumber() != null) ? "Día " + dayNumber : DEFAULT_DAY_NAME);
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("No puedes registrar entrenamientos en fechas futuras");
        }
    }

    private WorkoutLog getOwned(Long id, Long clientId) {
        WorkoutLog log = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado: " + id));
        if (!log.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Registro no encontrado: " + id);
        }
        return log;
    }

    private static String key(Long exerciseId, String name) {
        return exerciseId != null ? "id:" + exerciseId : nameKey(name);
    }

    private static String nameKey(String name) {
        return "name:" + name.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkoutLogResponse toResponse(WorkoutLog log) {
        Map<String, LoggedExercise> exercises = new LinkedHashMap<>();
        BigDecimal volume = BigDecimal.ZERO;
        for (WorkoutLogSet s : log.getSets()) {
            exercises.computeIfAbsent(s.getExerciseOrder() + "|" + s.getExerciseName(),
                            k -> new LoggedExercise(s.getExerciseId(), s.getExerciseName(), new ArrayList<>()))
                    .sets().add(new SetValue(s.getSetNumber(), s.getWeightKg(), s.getReps(), s.getRir()));
            if (s.getReps() != null) {
                volume = volume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
            }
        }
        return new WorkoutLogResponse(log.getId(), log.getClientId(), log.getWorkoutId(), log.getWorkoutName(),
                log.getDayNumber(), log.getDayName(), log.getPerformedOn(), log.getNotes(), log.getSets().size(),
                volume.setScale(1, RoundingMode.HALF_UP), new ArrayList<>(exercises.values()), log.getCreatedAt());
    }
}
