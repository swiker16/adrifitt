import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../../core/services/plan.service';
import { BILLING_PERIOD_LABEL, Plan, PlanPeriodPrice, splitFeature } from '../../../shared/models/plan.model';

@Component({
  selector: 'app-plan-list',
  imports: [RouterLink, DecimalPipe, MatIconModule],
  templateUrl: './plan-list.html',
  styleUrl: './plan-list.scss',
})
export class PlanList {
  private readonly planService = inject(PlanService);

  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly plans = signal<Plan[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.planService.findAll(false).subscribe({
      next: (data) => {
        this.plans.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  toggleActive(plan: Plan, event: Event): void {
    event.stopPropagation();
    const request = plan.active
      ? this.planService.deactivate(plan.id)
      : this.planService.activate(plan.id);
    request.subscribe((updated) => {
      this.plans.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
    });
  }

  deletePlan(plan: Plan, event: Event): void {
    event.stopPropagation();
    if (!confirm(`¿Eliminar permanentemente el plan "${plan.name}"? Esta acción no se puede deshacer.`)) return;
    this.planService.deletePermanently(plan.id).subscribe({
      next: () => this.plans.update((list) => list.filter((p) => p.id !== plan.id)),
      error: (err) => alert(err?.error?.message ?? 'No se pudo eliminar el plan.'),
    });
  }

  /** Prices of the non-monthly periods the plan offers. */
  longPrices(plan: Plan): PlanPeriodPrice[] {
    return (plan.prices ?? []).filter((p) => p.period !== 'MONTHLY');
  }

  title(feature: string): string {
    return splitFeature(feature).title;
  }
}
