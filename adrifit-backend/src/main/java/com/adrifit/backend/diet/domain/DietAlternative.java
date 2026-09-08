package com.adrifit.backend.diet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diet_alternatives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DietAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diet_food_id", nullable = false)
    private DietFood dietFood;

    @Column(name = "alternative_name", nullable = false, length = 200)
    private String alternativeName;

    @Column(precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(length = 50)
    private String unit;

    @Column(length = 500)
    private String notes;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;
}
