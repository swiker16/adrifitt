package com.adrifit.backend.diet.mapper;

import com.adrifit.backend.diet.domain.ClientDiet;
import com.adrifit.backend.diet.domain.Diet;
import com.adrifit.backend.diet.domain.DietAlternative;
import com.adrifit.backend.diet.domain.DietDay;
import com.adrifit.backend.diet.domain.DietFood;
import com.adrifit.backend.diet.domain.DietMeal;
import com.adrifit.backend.diet.dto.ClientDietResponse;
import com.adrifit.backend.diet.dto.DietAlternativeResponse;
import com.adrifit.backend.diet.dto.DietDayResponse;
import com.adrifit.backend.diet.dto.DietFoodResponse;
import com.adrifit.backend.diet.dto.DietMealResponse;
import com.adrifit.backend.diet.dto.DietResponse;
import com.adrifit.backend.diet.dto.DietSummaryResponse;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DietMapper {

    public DietResponse toResponse(Diet diet) {
        List<DietDayResponse> days = diet.getDays() == null ? Collections.emptyList()
                : diet.getDays().stream().map(this::toDayResponse).toList();

        BigDecimal totalCal = sum(days.stream().map(DietDayResponse::totalCalories).toList());
        BigDecimal totalProt = sum(days.stream().map(DietDayResponse::totalProtein).toList());
        BigDecimal totalCarbs = sum(days.stream().map(DietDayResponse::totalCarbs).toList());
        BigDecimal totalFat = sum(days.stream().map(DietDayResponse::totalFat).toList());

        return new DietResponse(
                diet.getId(),
                diet.getName(),
                diet.getDescription(),
                diet.getObjective(),
                diet.getActive(),
                diet.getTrainerId(),
                days,
                totalCal,
                totalProt,
                totalCarbs,
                totalFat,
                days.size(),
                diet.getCreatedAt(),
                diet.getUpdatedAt()
        );
    }

    public DietSummaryResponse toSummary(Diet diet) {
        int dayCount = diet.getDays() == null ? 0 : diet.getDays().size();
        BigDecimal avgCal = null;
        if (diet.getDays() != null && !diet.getDays().isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (DietDay day : diet.getDays()) {
                total = total.add(calcDayCalories(day));
            }
            avgCal = total.divide(BigDecimal.valueOf(dayCount), 2, java.math.RoundingMode.HALF_UP);
        }
        return new DietSummaryResponse(
                diet.getId(),
                diet.getName(),
                diet.getDescription(),
                diet.getObjective(),
                diet.getActive(),
                dayCount,
                avgCal,
                diet.getCreatedAt(),
                diet.getUpdatedAt()
        );
    }

    public ClientDietResponse toClientDietResponse(ClientDiet cd) {
        DietResponse dietResp = toResponse(cd.getDiet());
        return new ClientDietResponse(
                cd.getId(),
                cd.getClient().getId(),
                cd.getDiet().getId(),
                cd.getDiet().getName(),
                cd.getDiet().getObjective(),
                cd.getAssignedAt(),
                cd.getStartDate(),
                cd.getEndDate(),
                cd.getActive(),
                cd.getTrainerNotes(),
                dietResp
        );
    }

    private DietDayResponse toDayResponse(DietDay day) {
        List<DietMealResponse> meals = day.getMeals() == null ? Collections.emptyList()
                : day.getMeals().stream().map(this::toMealResponse).toList();

        BigDecimal totalCal = sum(meals.stream().map(DietMealResponse::totalCalories).toList());
        BigDecimal totalProt = sum(meals.stream().map(DietMealResponse::totalProtein).toList());
        BigDecimal totalCarbs = sum(meals.stream().map(DietMealResponse::totalCarbs).toList());
        BigDecimal totalFat = sum(meals.stream().map(DietMealResponse::totalFat).toList());

        return new DietDayResponse(
                day.getId(),
                day.getName(),
                day.getDayNumber(),
                day.getNotes(),
                day.getOrderIndex(),
                meals,
                totalCal,
                totalProt,
                totalCarbs,
                totalFat,
                meals.size()
        );
    }

    private DietMealResponse toMealResponse(DietMeal meal) {
        List<DietFoodResponse> foods = meal.getFoods() == null ? Collections.emptyList()
                : meal.getFoods().stream().map(this::toFoodResponse).toList();

        BigDecimal totalCal = sum(foods.stream().map(DietFoodResponse::calories).toList());
        BigDecimal totalProt = sum(foods.stream().map(DietFoodResponse::proteinGrams).toList());
        BigDecimal totalCarbs = sum(foods.stream().map(DietFoodResponse::carbsGrams).toList());
        BigDecimal totalFat = sum(foods.stream().map(DietFoodResponse::fatGrams).toList());

        return new DietMealResponse(
                meal.getId(),
                meal.getName(),
                meal.getTime(),
                meal.getNotes(),
                meal.getOrderIndex(),
                foods,
                totalCal,
                totalProt,
                totalCarbs,
                totalFat
        );
    }

    private DietFoodResponse toFoodResponse(DietFood food) {
        List<DietAlternativeResponse> alts = food.getAlternatives() == null ? Collections.emptyList()
                : food.getAlternatives().stream().map(this::toAltResponse).toList();

        return new DietFoodResponse(
                food.getId(),
                food.getFoodName(),
                food.getQuantity(),
                food.getUnit(),
                food.getCalories(),
                food.getProteinGrams(),
                food.getCarbsGrams(),
                food.getFatGrams(),
                food.getNotes(),
                food.getOrderIndex(),
                alts
        );
    }

    private DietAlternativeResponse toAltResponse(DietAlternative alt) {
        return new DietAlternativeResponse(
                alt.getId(),
                alt.getAlternativeName(),
                alt.getQuantity(),
                alt.getUnit(),
                alt.getNotes(),
                alt.getOrderIndex()
        );
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcDayCalories(DietDay day) {
        if (day.getMeals() == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DietMeal meal : day.getMeals()) {
            if (meal.getFoods() == null) continue;
            for (DietFood food : meal.getFoods()) {
                if (food.getCalories() != null) total = total.add(food.getCalories());
            }
        }
        return total;
    }
}
