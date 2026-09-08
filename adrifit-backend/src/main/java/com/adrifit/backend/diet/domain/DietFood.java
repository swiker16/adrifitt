package com.adrifit.backend.diet.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diet_foods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DietFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diet_meal_id", nullable = false)
    private DietMeal dietMeal;

    @Column(name = "food_name", nullable = false, length = 200)
    private String foodName;

    @Column(precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(length = 50)
    private String unit;

    @Column(precision = 7, scale = 2)
    private BigDecimal calories;

    @Column(name = "protein_grams", precision = 6, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbs_grams", precision = 6, scale = 2)
    private BigDecimal carbsGrams;

    @Column(name = "fat_grams", precision = 6, scale = 2)
    private BigDecimal fatGrams;

    @Column(length = 500)
    private String notes;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Builder.Default
    @OneToMany(mappedBy = "dietFood", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<DietAlternative> alternatives = new ArrayList<>();
}
