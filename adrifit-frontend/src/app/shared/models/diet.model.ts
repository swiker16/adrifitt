export interface DietAlternative {
  id: number;
  alternativeName: string;
  quantity?: number;
  unit?: string;
  notes?: string;
  orderIndex: number;
}

export interface DietFood {
  id: number;
  foodName: string;
  quantity?: number;
  unit?: string;
  calories?: number;
  proteinGrams?: number;
  carbsGrams?: number;
  fatGrams?: number;
  notes?: string;
  orderIndex: number;
  alternatives: DietAlternative[];
}

export interface DietMeal {
  id: number;
  name: string;
  time?: string;
  notes?: string;
  orderIndex: number;
  foods: DietFood[];
  totalCalories?: number;
  totalProtein?: number;
  totalCarbs?: number;
  totalFat?: number;
}

export interface DietDay {
  id: number;
  name: string;
  dayNumber?: number;
  notes?: string;
  orderIndex: number;
  meals: DietMeal[];
  totalCalories?: number;
  totalProtein?: number;
  totalCarbs?: number;
  totalFat?: number;
  mealCount?: number;
}

export interface Diet {
  id: number;
  name: string;
  description?: string;
  objective?: string;
  active: boolean;
  trainerId?: number;
  days: DietDay[];
  totalCalories?: number;
  totalProtein?: number;
  totalCarbs?: number;
  totalFat?: number;
  dayCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface DietSummary {
  id: number;
  name: string;
  description?: string;
  objective?: string;
  active: boolean;
  dayCount: number;
  avgCalories?: number;
  createdAt: string;
  updatedAt: string;
}

export interface ClientDiet {
  id: number;
  clientId: number;
  dietId: number;
  dietName: string;
  dietObjective?: string;
  assignedAt: string;
  startDate?: string;
  endDate?: string;
  active: boolean;
  trainerNotes?: string;
  diet: Diet;
}

export interface CreateDietRequest {
  name: string;
  description?: string;
  objective?: string;
  days?: CreateDietDayRequest[];
}

export interface CreateDietDayRequest {
  id?: number;
  name: string;
  dayNumber?: number;
  notes?: string;
  orderIndex?: number;
  meals?: CreateDietMealRequest[];
}

export interface CreateDietMealRequest {
  id?: number;
  name: string;
  time?: string;
  notes?: string;
  orderIndex?: number;
  foods?: CreateDietFoodRequest[];
}

export interface CreateDietFoodRequest {
  id?: number;
  foodName: string;
  quantity?: number;
  unit?: string;
  calories?: number;
  proteinGrams?: number;
  carbsGrams?: number;
  fatGrams?: number;
  notes?: string;
  orderIndex?: number;
  alternatives?: CreateDietAlternativeRequest[];
}

export interface CreateDietAlternativeRequest {
  id?: number;
  alternativeName: string;
  quantity?: number;
  unit?: string;
  notes?: string;
  orderIndex?: number;
}

export interface AssignDietRequest {
  dietId: number;
  startDate?: string;
  trainerNotes?: string;
}
