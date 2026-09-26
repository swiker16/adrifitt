package com.adrifit.backend.diet.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.diet.domain.ClientDiet;
import com.adrifit.backend.diet.domain.Diet;
import com.adrifit.backend.diet.domain.DietAlternative;
import com.adrifit.backend.diet.domain.DietDay;
import com.adrifit.backend.diet.domain.DietFood;
import com.adrifit.backend.diet.domain.DietMeal;
import com.adrifit.backend.diet.dto.AssignDietRequest;
import com.adrifit.backend.diet.dto.ClientDietResponse;
import com.adrifit.backend.diet.dto.CreateDietRequest;
import com.adrifit.backend.diet.dto.DietAlternativeRequest;
import com.adrifit.backend.diet.dto.DietDayRequest;
import com.adrifit.backend.diet.dto.DietFoodRequest;
import com.adrifit.backend.diet.dto.DietMealRequest;
import com.adrifit.backend.diet.dto.DietResponse;
import com.adrifit.backend.diet.dto.DietSummaryResponse;
import com.adrifit.backend.diet.mapper.DietMapper;
import com.adrifit.backend.diet.repository.ClientDietRepository;
import com.adrifit.backend.diet.repository.DietRepository;
import com.adrifit.backend.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DietService {

    private final DietRepository dietRepository;
    private final ClientDietRepository clientDietRepository;
    private final DietMapper dietMapper;
    private final ClientService clientService;
    private final UserService userService;

    public DietService(DietRepository dietRepository,
                       ClientDietRepository clientDietRepository,
                       DietMapper dietMapper,
                       ClientService clientService,
                       UserService userService) {
        this.dietRepository = dietRepository;
        this.clientDietRepository = clientDietRepository;
        this.dietMapper = dietMapper;
        this.clientService = clientService;
        this.userService = userService;
    }

    public List<DietSummaryResponse> findAll() {
        return dietRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(dietMapper::toSummary).toList();
    }

    public DietResponse findById(Long id) {
        return dietMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public DietResponse create(CreateDietRequest request) {
        Diet diet = Diet.builder()
                .name(request.name())
                .description(request.description())
                .objective(request.objective())
                .active(true)
                .build();
        applyDays(diet, request.days());
        return dietMapper.toResponse(dietRepository.save(diet));
    }

    @Transactional
    public DietResponse update(Long id, CreateDietRequest request) {
        Diet diet = getOrThrow(id);
        diet.setName(request.name());
        diet.setDescription(request.description());
        diet.setObjective(request.objective());
        diet.getDays().clear();
        applyDays(diet, request.days());
        return dietMapper.toResponse(dietRepository.save(diet));
    }

    @Transactional
    public void delete(Long id) {
        Diet diet = getOrThrow(id);
        if (clientDietRepository.existsByDiet_IdAndActiveTrue(id)) {
            throw new IllegalStateException("No se puede eliminar una dieta activamente asignada a un cliente.");
        }
        if (clientDietRepository.existsByDiet_Id(id)) {
            throw new IllegalStateException("Esta dieta tiene historial de asignaciones. Desactívala en lugar de eliminarla.");
        }
        dietRepository.delete(diet);
    }

    @Transactional
    public DietResponse duplicate(Long id) {
        Diet source = getOrThrow(id);
        Diet copy = Diet.builder()
                .name(source.getName() + " (copia)")
                .description(source.getDescription())
                .objective(source.getObjective())
                .active(true)
                .trainerId(source.getTrainerId())
                .build();

        for (DietDay srcDay : source.getDays()) {
            DietDay newDay = DietDay.builder()
                    .diet(copy)
                    .name(srcDay.getName())
                    .dayNumber(srcDay.getDayNumber())
                    .notes(srcDay.getNotes())
                    .orderIndex(srcDay.getOrderIndex())
                    .build();
            for (DietMeal srcMeal : srcDay.getMeals()) {
                DietMeal newMeal = DietMeal.builder()
                        .dietDay(newDay)
                        .name(srcMeal.getName())
                        .time(srcMeal.getTime())
                        .notes(srcMeal.getNotes())
                        .orderIndex(srcMeal.getOrderIndex())
                        .build();
                for (DietFood srcFood : srcMeal.getFoods()) {
                    DietFood newFood = DietFood.builder()
                            .dietMeal(newMeal)
                            .foodName(srcFood.getFoodName())
                            .quantity(srcFood.getQuantity())
                            .unit(srcFood.getUnit())
                            .calories(srcFood.getCalories())
                            .proteinGrams(srcFood.getProteinGrams())
                            .carbsGrams(srcFood.getCarbsGrams())
                            .fatGrams(srcFood.getFatGrams())
                            .notes(srcFood.getNotes())
                            .orderIndex(srcFood.getOrderIndex())
                            .build();
                    for (DietAlternative srcAlt : srcFood.getAlternatives()) {
                        DietAlternative newAlt = DietAlternative.builder()
                                .dietFood(newFood)
                                .alternativeName(srcAlt.getAlternativeName())
                                .quantity(srcAlt.getQuantity())
                                .unit(srcAlt.getUnit())
                                .notes(srcAlt.getNotes())
                                .orderIndex(srcAlt.getOrderIndex())
                                .build();
                        newFood.getAlternatives().add(newAlt);
                    }
                    newMeal.getFoods().add(newFood);
                }
                newDay.getMeals().add(newMeal);
            }
            copy.getDays().add(newDay);
        }
        return dietMapper.toResponse(dietRepository.save(copy));
    }

    @Transactional
    public DietResponse toggleStatus(Long id) {
        Diet diet = getOrThrow(id);
        diet.setActive(!diet.getActive());
        return dietMapper.toResponse(dietRepository.save(diet));
    }

    @Transactional
    public ClientDietResponse assignToClient(Long clientId, AssignDietRequest request) {
        Client client = clientService.getEntityById(clientId);
        Diet diet = getOrThrow(request.dietId());
        if (!diet.getActive()) {
            throw new IllegalStateException("No se puede asignar una dieta inactiva.");
        }

        clientDietRepository.findByClient_IdAndActiveTrue(clientId).ifPresent(cd -> {
            cd.setActive(false);
            cd.setEndDate(LocalDate.now());
            clientDietRepository.save(cd);
        });

        ClientDiet cd = ClientDiet.builder()
                .client(client)
                .diet(diet)
                .assignedAt(Instant.now())
                .startDate(request.startDate() != null ? request.startDate() : LocalDate.now())
                .active(true)
                .trainerNotes(request.trainerNotes())
                .build();
        return dietMapper.toClientDietResponse(clientDietRepository.save(cd));
    }

    public ClientDietResponse getActiveForClient(Long clientId) {
        assertCanAccessClient(clientId);
        return clientDietRepository.findByClient_IdAndActiveTrue(clientId)
                .map(dietMapper::toClientDietResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active diet for client: " + clientId));
    }

    public ClientDietResponse getMyDiet() {
        Long clientId = getCurrentClientId();
        return getActiveForClient(clientId);
    }

    public List<ClientDietResponse> getHistoryForClient(Long clientId) {
        assertCanAccessClient(clientId);
        return clientDietRepository.findAllByClient_IdOrderByAssignedAtDesc(clientId)
                .stream().map(dietMapper::toClientDietResponse).toList();
    }

    public Diet getEntityById(Long id) {
        return getOrThrow(id);
    }

    private void applyDays(Diet diet, List<DietDayRequest> dayRequests) {
        if (dayRequests == null) return;
        List<DietDay> days = new ArrayList<>();
        for (int di = 0; di < dayRequests.size(); di++) {
            DietDayRequest dr = dayRequests.get(di);
            DietDay day = DietDay.builder()
                    .diet(diet)
                    .name(dr.name())
                    .dayNumber(dr.dayNumber())
                    .notes(dr.notes())
                    .orderIndex(dr.orderIndex() != null ? dr.orderIndex() : di)
                    .build();
            applyMeals(day, dr.meals());
            days.add(day);
        }
        diet.getDays().addAll(days);
    }

    private void applyMeals(DietDay day, List<DietMealRequest> mealRequests) {
        if (mealRequests == null) return;
        for (int mi = 0; mi < mealRequests.size(); mi++) {
            DietMealRequest mr = mealRequests.get(mi);
            DietMeal meal = DietMeal.builder()
                    .dietDay(day)
                    .name(mr.name())
                    .time(mr.time())
                    .notes(mr.notes())
                    .orderIndex(mr.orderIndex() != null ? mr.orderIndex() : mi)
                    .build();
            applyFoods(meal, mr.foods());
            day.getMeals().add(meal);
        }
    }

    private void applyFoods(DietMeal meal, List<DietFoodRequest> foodRequests) {
        if (foodRequests == null) return;
        for (int fi = 0; fi < foodRequests.size(); fi++) {
            DietFoodRequest fr = foodRequests.get(fi);
            DietFood food = DietFood.builder()
                    .dietMeal(meal)
                    .foodName(fr.foodName())
                    .quantity(fr.quantity())
                    .unit(fr.unit())
                    .calories(fr.calories())
                    .proteinGrams(fr.proteinGrams())
                    .carbsGrams(fr.carbsGrams())
                    .fatGrams(fr.fatGrams())
                    .notes(fr.notes())
                    .orderIndex(fr.orderIndex() != null ? fr.orderIndex() : fi)
                    .build();
            applyAlternatives(food, fr.alternatives());
            meal.getFoods().add(food);
        }
    }

    private void applyAlternatives(DietFood food, List<DietAlternativeRequest> altRequests) {
        if (altRequests == null) return;
        for (int ai = 0; ai < altRequests.size(); ai++) {
            DietAlternativeRequest ar = altRequests.get(ai);
            if (ar.alternativeName() == null || ar.alternativeName().isBlank()) continue;
            DietAlternative alt = DietAlternative.builder()
                    .dietFood(food)
                    .alternativeName(ar.alternativeName())
                    .quantity(ar.quantity())
                    .unit(ar.unit())
                    .notes(ar.notes())
                    .orderIndex(ar.orderIndex() != null ? ar.orderIndex() : ai)
                    .build();
            food.getAlternatives().add(alt);
        }
    }

    private Diet getOrThrow(Long id) {
        return dietRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diet not found: " + id));
    }

    @org.springframework.context.event.EventListener
    @Transactional
    public void onClientDeleted(com.adrifit.backend.common.event.ClientDeletedEvent event) {
        clientDietRepository.deleteAll(clientDietRepository.findAllByClient_IdOrderByAssignedAtDesc(event.clientId()));
    }

    public void assertCanAccessClient(Long clientId) {
        if (SecurityUtils.isTrainer()) return;
        if (!getCurrentClientId().equals(clientId)) {
            throw new AccessDeniedException("You can only access your own diet");
        }
    }

    private Long getCurrentClientId() {
        return clientService.getByUserId(userService.getCurrentUser().getId()).getId();
    }
}
