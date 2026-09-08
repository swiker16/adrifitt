package com.adrifit.backend.workout.service;

import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.user.service.UserService;
import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.domain.Workout;
import com.adrifit.backend.workout.domain.WorkoutExercise;
import com.adrifit.backend.workout.dto.AssignWorkoutRequest;
import com.adrifit.backend.workout.dto.ClientWorkoutResponse;
import com.adrifit.backend.workout.dto.CreateWorkoutRequest;
import com.adrifit.backend.workout.dto.WorkoutExerciseRequest;
import com.adrifit.backend.workout.dto.WorkoutResponse;
import com.adrifit.backend.workout.mapper.WorkoutMapper;
import com.adrifit.backend.workout.repository.ClientWorkoutRepository;
import com.adrifit.backend.workout.repository.WorkoutRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final ClientWorkoutRepository clientWorkoutRepository;
    private final WorkoutMapper workoutMapper;
    private final ClientService clientService;
    private final UserService userService;

    public WorkoutService(WorkoutRepository workoutRepository,
                          ClientWorkoutRepository clientWorkoutRepository,
                          WorkoutMapper workoutMapper,
                          ClientService clientService,
                          UserService userService) {
        this.workoutRepository = workoutRepository;
        this.clientWorkoutRepository = clientWorkoutRepository;
        this.workoutMapper = workoutMapper;
        this.clientService = clientService;
        this.userService = userService;
    }

    public List<WorkoutResponse> findAll() {
        return workoutRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(workoutMapper::toResponse).toList();
    }

    public WorkoutResponse findById(Long id) {
        return workoutMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public WorkoutResponse create(CreateWorkoutRequest request) {
        Workout workout = Workout.builder()
                .name(request.name())
                .description(request.description())
                .objective(request.objective())
                .daysPerWeek(request.daysPerWeek())
                .build();
        applyExercises(workout, request.exercises());
        return workoutMapper.toResponse(workoutRepository.save(workout));
    }

    @Transactional
    public WorkoutResponse update(Long id, CreateWorkoutRequest request) {
        Workout workout = getOrThrow(id);
        workout.setName(request.name());
        workout.setDescription(request.description());
        workout.setObjective(request.objective());
        workout.setDaysPerWeek(request.daysPerWeek());
        workout.getExercises().clear();
        applyExercises(workout, request.exercises());
        return workoutMapper.toResponse(workoutRepository.save(workout));
    }

    @Transactional
    public void delete(Long id) {
        Workout workout = getOrThrow(id);
        List<ClientWorkout> assignments = clientWorkoutRepository.findAllByWorkoutId(id);
        clientWorkoutRepository.deleteAll(assignments);
        workoutRepository.delete(workout);
    }

    @Transactional
    public WorkoutResponse duplicate(Long id) {
        Workout source = getOrThrow(id);
        Workout copy = Workout.builder()
                .name(source.getName() + " (copia)")
                .description(source.getDescription())
                .objective(source.getObjective())
                .build();
        source.getExercises().forEach(e -> {
            WorkoutExercise ex = WorkoutExercise.builder()
                    .workout(copy)
                    .exerciseName(e.getExerciseName())
                    .sets(e.getSets())
                    .reps(e.getReps())
                    .rir(e.getRir())
                    .restSeconds(e.getRestSeconds())
                    .notes(e.getNotes())
                    .orderIndex(e.getOrderIndex())
                    .build();
            copy.getExercises().add(ex);
        });
        return workoutMapper.toResponse(workoutRepository.save(copy));
    }

    @Transactional
    public ClientWorkoutResponse assignToClient(Long clientId, AssignWorkoutRequest request) {
        clientService.getEntityById(clientId);
        Workout workout = getOrThrow(request.workoutId());

        clientWorkoutRepository.findByClientIdAndActiveTrue(clientId).ifPresent(cw -> {
            cw.setActive(false);
            cw.setEndDate(LocalDate.now());
            clientWorkoutRepository.save(cw);
        });

        ClientWorkout cw = ClientWorkout.builder()
                .clientId(clientId)
                .workout(workout)
                .active(true)
                .build();
        return workoutMapper.toClientWorkoutResponse(clientWorkoutRepository.save(cw));
    }

    public ClientWorkoutResponse getActiveForClient(Long clientId) {
        assertCanAccessClient(clientId);
        return clientWorkoutRepository.findByClientIdAndActiveTrue(clientId)
                .map(workoutMapper::toClientWorkoutResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active workout for client: " + clientId));
    }

    public ClientWorkoutResponse getMyWorkout() {
        Long clientId = getCurrentClientId();
        return getActiveForClient(clientId);
    }

    public List<ClientWorkoutResponse> getHistoryForClient(Long clientId) {
        return clientWorkoutRepository.findAllByClientIdOrderByAssignedAtDesc(clientId)
                .stream().map(workoutMapper::toClientWorkoutResponse).toList();
    }

    public Workout getEntityById(Long id) {
        return getOrThrow(id);
    }

    private void applyExercises(Workout workout, List<WorkoutExerciseRequest> requests) {
        if (requests == null) return;
        List<WorkoutExercise> list = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            WorkoutExerciseRequest r = requests.get(i);
            list.add(WorkoutExercise.builder()
                    .workout(workout)
                    .exerciseName(r.exerciseName())
                    .sets(r.sets())
                    .reps(r.reps())
                    .rir(r.rir())
                    .restSeconds(r.restSeconds())
                    .notes(r.notes())
                    .orderIndex(r.orderIndex() != null ? r.orderIndex() : i)
                    .dayNumber(r.dayNumber())
                    .dayName(r.dayName())
                    .warmUpSets(r.warmUpSets())
                    .approxReps(r.approxReps())
                    .build());
        }
        workout.getExercises().addAll(list);
    }

    private Workout getOrThrow(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout not found with id: " + id));
    }

    private void assertCanAccessClient(Long clientId) {
        if (SecurityUtils.isTrainer()) return;
        if (!getCurrentClientId().equals(clientId)) {
            throw new AccessDeniedException("You can only access your own workout");
        }
    }

    private Long getCurrentClientId() {
        return clientService.getByUserId(userService.getCurrentUser().getId()).getId();
    }
}
