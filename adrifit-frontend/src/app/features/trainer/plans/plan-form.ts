import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../../core/services/plan.service';
import { PlanRequest } from '../../../shared/models/plan.model';

@Component({
  selector: 'app-plan-form',
  imports: [RouterLink, ReactiveFormsModule, MatIconModule],
  templateUrl: './plan-form.html',
  styleUrl: './plan-form.scss',
})
export class PlanForm {
  private readonly fb = inject(FormBuilder);
  private readonly planService = inject(PlanService);
  private readonly router = inject(Router);

  // Present only on the edit route (/trainer/plans/:id/edit).
  readonly id = input<string>();

  readonly isEdit = computed(() => !!this.id());
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(1000)]],
    monthlyPrice: [0, [Validators.required, Validators.min(0)]],
    reviewFrequencyDays: [15, [Validators.required, Validators.min(1)]],
    messagingEnabled: [false],
    analyticsEnabled: [false],
    pdfExportEnabled: [false],
    prioritySupport: [false],
    active: [true],
  });

  constructor() {
    queueMicrotask(() => {
      if (this.id()) {
        this.loadPlan(Number(this.id()));
      }
    });
  }

  private loadPlan(planId: number): void {
    this.planService.findById(planId).subscribe({
      next: (plan) => this.form.patchValue(plan),
      error: () => this.error.set('No se pudo cargar el plan.'),
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const payload = this.form.getRawValue() as PlanRequest;

    const request = this.isEdit()
      ? this.planService.update(Number(this.id()), payload)
      : this.planService.create(payload);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigateByUrl('/trainer/plans');
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(
          err?.status === 409 ? 'Ya existe un plan con ese nombre.' : 'No se pudo guardar el plan.'
        );
      },
    });
  }
}
