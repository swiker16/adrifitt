import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { DietService } from '../../../core/services/diet.service';
import { CreateDietRequest } from '../../../shared/models/diet.model';

@Component({
  selector: 'app-diet-form',
  imports: [RouterLink, ReactiveFormsModule, MatIconModule],
  templateUrl: './diet-form.html',
  styleUrl: './diet-form.scss',
})
export class DietForm {
  private readonly fb = inject(FormBuilder);
  private readonly dietService = inject(DietService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly editId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly activeDayIndex = signal(0);

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    objective: ['', Validators.maxLength(200)],
    days: this.fb.array([]),
  });

  get days(): FormArray { return this.form.get('days') as FormArray; }

  getMeals(dayIndex: number): FormArray {
    return this.days.at(dayIndex).get('meals') as FormArray;
  }

  getFoods(dayIndex: number, mealIndex: number): FormArray {
    return this.getMeals(dayIndex).at(mealIndex).get('foods') as FormArray;
  }

  getAlternatives(dayIndex: number, mealIndex: number, foodIndex: number): FormArray {
    return this.getFoods(dayIndex, mealIndex).at(foodIndex).get('alternatives') as FormArray;
  }

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId.set(+id);
      this.loading.set(true);
      this.dietService.findById(+id).subscribe({
        next: (diet) => {
          this.form.patchValue({ name: diet.name, description: diet.description ?? '', objective: diet.objective ?? '' });
          diet.days?.forEach(day => {
            const dayFg = this.buildDay(day.name, day.dayNumber, day.notes);
            const mealsArr = dayFg.get('meals') as FormArray;
            day.meals?.forEach(meal => {
              const mealFg = this.buildMeal(meal.name, meal.time, meal.notes);
              const foodsArr = mealFg.get('foods') as FormArray;
              meal.foods?.forEach(food => {
                const foodFg = this.buildFood(food.foodName, food.quantity, food.unit,
                  food.calories, food.proteinGrams, food.carbsGrams, food.fatGrams, food.notes);
                const altsArr = foodFg.get('alternatives') as FormArray;
                food.alternatives?.forEach(alt => {
                  altsArr.push(this.buildAlt(alt.alternativeName, alt.quantity, alt.unit, alt.notes));
                });
                foodsArr.push(foodFg);
              });
              mealsArr.push(mealFg);
            });
            this.days.push(dayFg);
          });
          this.loading.set(false);
        },
        error: () => { this.error.set('No se pudo cargar la dieta.'); this.loading.set(false); },
      });
    }
  }

  addDay(): void {
    this.days.push(this.buildDay());
    this.activeDayIndex.set(this.days.length - 1);
  }

  removeDay(i: number): void {
    this.days.removeAt(i);
    const active = Math.min(this.activeDayIndex(), this.days.length - 1);
    this.activeDayIndex.set(Math.max(0, active));
  }

  addMeal(dayIndex: number): void {
    this.getMeals(dayIndex).push(this.buildMeal());
  }

  removeMeal(dayIndex: number, mealIndex: number): void {
    this.getMeals(dayIndex).removeAt(mealIndex);
  }

  addFood(dayIndex: number, mealIndex: number): void {
    this.getFoods(dayIndex, mealIndex).push(this.buildFood());
  }

  removeFood(dayIndex: number, mealIndex: number, foodIndex: number): void {
    this.getFoods(dayIndex, mealIndex).removeAt(foodIndex);
  }

  addAlternative(dayIndex: number, mealIndex: number, foodIndex: number): void {
    this.getAlternatives(dayIndex, mealIndex, foodIndex).push(this.buildAlt());
  }

  removeAlternative(dayIndex: number, mealIndex: number, foodIndex: number, altIndex: number): void {
    this.getAlternatives(dayIndex, mealIndex, foodIndex).removeAt(altIndex);
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.error.set('');
    const request = this.buildRequest();
    const op = this.editId()
      ? this.dietService.update(this.editId()!, request)
      : this.dietService.create(request);
    op.subscribe({
      next: (d) => this.router.navigate(['/trainer/diets', d.id]),
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Error al guardar la dieta.');
        this.saving.set(false);
      },
    });
  }

  private buildRequest(): CreateDietRequest {
    const v = this.form.value;
    return {
      name: v.name,
      description: v.description || undefined,
      objective: v.objective || undefined,
      days: v.days?.map((d: any, di: number) => ({
        name: d.name,
        dayNumber: d.dayNumber ? +d.dayNumber : di + 1,
        notes: d.notes || undefined,
        orderIndex: di,
        meals: d.meals?.map((m: any, mi: number) => ({
          name: m.name,
          time: m.time || undefined,
          notes: m.notes || undefined,
          orderIndex: mi,
          foods: m.foods?.map((f: any, fi: number) => ({
            foodName: f.foodName,
            quantity: f.quantity ? +f.quantity : undefined,
            unit: f.unit || undefined,
            calories: f.calories ? +f.calories : undefined,
            proteinGrams: f.proteinGrams ? +f.proteinGrams : undefined,
            carbsGrams: f.carbsGrams ? +f.carbsGrams : undefined,
            fatGrams: f.fatGrams ? +f.fatGrams : undefined,
            notes: f.notes || undefined,
            orderIndex: fi,
            alternatives: f.alternatives?.filter((a: any) => a.alternativeName?.trim())
              .map((a: any, ai: number) => ({
                alternativeName: a.alternativeName,
                quantity: a.quantity ? +a.quantity : undefined,
                unit: a.unit || undefined,
                notes: a.notes || undefined,
                orderIndex: ai,
              })),
          })),
        })),
      })),
    };
  }

  private buildDay(name = '', dayNumber?: number, notes?: string): FormGroup {
    return this.fb.group({
      name: [name, [Validators.required, Validators.maxLength(200)]],
      dayNumber: [dayNumber ?? null],
      notes: [notes ?? ''],
      meals: this.fb.array([]),
    });
  }

  private buildMeal(name = '', time?: string, notes?: string): FormGroup {
    return this.fb.group({
      name: [name, [Validators.required, Validators.maxLength(200)]],
      time: [time ?? ''],
      notes: [notes ?? ''],
      foods: this.fb.array([]),
    });
  }

  private buildFood(
    foodName = '', quantity?: number, unit?: string,
    calories?: number, proteinGrams?: number, carbsGrams?: number, fatGrams?: number, notes?: string
  ): FormGroup {
    return this.fb.group({
      foodName: [foodName, [Validators.required, Validators.maxLength(200)]],
      quantity: [quantity ?? null],
      unit: [unit ?? ''],
      calories: [calories ?? null],
      proteinGrams: [proteinGrams ?? null],
      carbsGrams: [carbsGrams ?? null],
      fatGrams: [fatGrams ?? null],
      notes: [notes ?? ''],
      alternatives: this.fb.array([]),
    });
  }

  private buildAlt(name = '', quantity?: number, unit?: string, notes?: string): FormGroup {
    return this.fb.group({
      alternativeName: [name, Validators.maxLength(200)],
      quantity: [quantity ?? null],
      unit: [unit ?? ''],
      notes: [notes ?? ''],
    });
  }
}
