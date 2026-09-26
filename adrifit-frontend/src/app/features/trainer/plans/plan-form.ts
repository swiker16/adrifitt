import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../../core/services/plan.service';
import {
  BILLING_PERIOD_LABEL,
  BILLING_PERIOD_MONTHS,
  BILLING_PERIOD_SUFFIX,
  BillingPeriod,
  Plan,
  PlanRequest,
} from '../../../shared/models/plan.model';

type PriceKey = 'monthlyPrice' | 'quarterlyPrice' | 'semiannualPrice' | 'annualPrice';

interface PriceField {
  key: PriceKey;
  period: BillingPeriod;
}

const PRICE_FIELDS: PriceField[] = [
  { key: 'monthlyPrice', period: 'MONTHLY' },
  { key: 'quarterlyPrice', period: 'QUARTERLY' },
  { key: 'semiannualPrice', period: 'SEMIANNUAL' },
  { key: 'annualPrice', period: 'ANNUAL' },
];

@Component({
  selector: 'app-plan-form',
  imports: [RouterLink, ReactiveFormsModule, MatIconModule, DecimalPipe],
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

  readonly priceFields = PRICE_FIELDS;
  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;

  readonly form = this.fb.group({
    name: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    description: this.fb.nonNullable.control('', [Validators.maxLength(1000)]),
    monthlyPrice: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    quarterlyPrice: this.fb.control<number | null>(null, [Validators.min(0)]),
    semiannualPrice: this.fb.control<number | null>(null, [Validators.min(0)]),
    annualPrice: this.fb.control<number | null>(null, [Validators.min(0)]),
    features: this.fb.array<FormControl<string>>([]),
    reviewFrequencyDays: this.fb.nonNullable.control(15, [Validators.required, Validators.min(1)]),
    messagingEnabled: this.fb.nonNullable.control(false),
    analyticsEnabled: this.fb.nonNullable.control(false),
    pdfExportEnabled: this.fb.nonNullable.control(false),
    prioritySupport: this.fb.nonNullable.control(false),
    active: this.fb.nonNullable.control(true),
  });

  get featureControls(): FormControl<string>[] {
    return this.form.controls.features.controls;
  }

  /** Live form value (drives the €/mes equivalent and saving % hints). */
  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  constructor() {
    queueMicrotask(() => {
      if (this.id()) {
        this.loadPlan(Number(this.id()));
      } else {
        this.addFeature();
      }
    });
  }

  private loadPlan(planId: number): void {
    this.planService.findById(planId).subscribe({
      next: (plan: Plan) => {
        const arr = this.form.controls.features;
        arr.clear();
        (plan.features ?? []).forEach((f) => arr.push(this.fb.nonNullable.control(f)));
        if (arr.length === 0) this.addFeature();
        this.form.patchValue({
          name: plan.name,
          description: plan.description ?? '',
          monthlyPrice: plan.monthlyPrice,
          quarterlyPrice: plan.quarterlyPrice,
          semiannualPrice: plan.semiannualPrice,
          annualPrice: plan.annualPrice,
          reviewFrequencyDays: plan.reviewFrequencyDays,
          messagingEnabled: plan.messagingEnabled,
          analyticsEnabled: plan.analyticsEnabled,
          pdfExportEnabled: plan.pdfExportEnabled,
          prioritySupport: plan.prioritySupport,
          active: plan.active,
        });
      },
      error: () => this.error.set('No se pudo cargar el plan.'),
    });
  }

  // ── Prices ──────────────────────────────────────────────────────────────

  private num(v: unknown): number | null {
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  /** €/mes equivalent of a period price (null when the period is not offered). */
  monthlyEquivalent(f: PriceField): number | null {
    const price = this.num(this.value()[f.key]);
    if (price === null) return null;
    return price / BILLING_PERIOD_MONTHS[f.period];
  }

  /** Saving vs. paying monthly, in % (null when it can't be computed). */
  savingPct(f: PriceField): number | null {
    if (f.period === 'MONTHLY') return null;
    const monthly = this.num(this.value().monthlyPrice);
    const price = this.num(this.value()[f.key]);
    if (!monthly || price === null) return null;
    const full = monthly * BILLING_PERIOD_MONTHS[f.period];
    return Math.round(((full - price) / full) * 1000) / 10;
  }

  // ── Features ────────────────────────────────────────────────────────────

  addFeature(): void {
    this.form.controls.features.push(this.fb.nonNullable.control(''));
  }

  removeFeature(i: number): void {
    this.form.controls.features.removeAt(i);
  }

  moveFeature(i: number, delta: number): void {
    const arr = this.form.controls.features;
    const j = i + delta;
    if (j < 0 || j >= arr.length) return;
    const ctrl = arr.at(i);
    arr.removeAt(i);
    arr.insert(j, ctrl);
  }

  // ── Save ────────────────────────────────────────────────────────────────

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const features = v.features.map((f) => f.trim()).filter((f) => f.length > 0);
    const payload: PlanRequest = {
      name: v.name.trim(),
      description: v.description,
      monthlyPrice: this.num(v.monthlyPrice) ?? 0,
      quarterlyPrice: this.num(v.quarterlyPrice),
      semiannualPrice: this.num(v.semiannualPrice),
      annualPrice: this.num(v.annualPrice),
      features: features.length ? features.join('\n') : null,
      reviewFrequencyDays: v.reviewFrequencyDays,
      messagingEnabled: v.messagingEnabled,
      analyticsEnabled: v.analyticsEnabled,
      pdfExportEnabled: v.pdfExportEnabled,
      prioritySupport: v.prioritySupport,
      active: v.active,
    };

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
          err?.status === 409
            ? 'Ya existe un plan con ese nombre.'
            : (err?.error?.message ?? 'No se pudo guardar el plan.')
        );
      },
    });
  }
}
