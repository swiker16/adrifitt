import { Component, computed, effect, input, model } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import {
  BILLING_PERIOD_LABEL,
  BILLING_PERIOD_MONTHS,
  BILLING_PERIOD_SUFFIX,
  BILLING_PERIODS,
  BillingPeriod,
  Plan,
  PlanPeriodPrice,
} from '../../../shared/models/plan.model';

/**
 * Plan cards + billing period selector + optional "special conditions" (custom price per period and note).
 * Used by the trainer when creating a client and when changing a client's plan.
 */
@Component({
  selector: 'app-plan-pricing-picker',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './plan-pricing-picker.html',
  styleUrl: './plan-pricing-picker.scss',
})
export class PlanPricingPicker {
  readonly plans = input.required<Plan[]>();
  /** Plan id of the client's current subscription (flagged in the cards). */
  readonly currentPlanId = input<number | null>(null);
  /** Show validation errors (set by the parent after a submit attempt). */
  readonly showErrors = input(false);

  readonly planId = model<number | null>(null);
  readonly period = model<BillingPeriod>('MONTHLY');
  readonly special = model(false);
  readonly customPrice = model<number | null>(null);
  readonly customNote = model('');

  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;
  readonly allPeriods = BILLING_PERIODS;

  readonly selectedPlan = computed(() => this.plans().find((p) => p.id === this.planId()) ?? null);

  /** Price of the chosen plan for the chosen period. */
  readonly planPrice = computed<PlanPeriodPrice | null>(() => this.priceOf(this.selectedPlan(), this.period()));

  readonly customMonthly = computed(() => {
    const c = this.customPrice();
    return c === null ? null : c / BILLING_PERIOD_MONTHS[this.period()];
  });

  readonly customInvalid = computed(() => {
    if (!this.special()) return false;
    const c = this.customPrice();
    return c === null || !Number.isFinite(c) || c < 0;
  });

  constructor() {
    // Keep the period valid for the chosen plan.
    effect(() => {
      const plan = this.selectedPlan();
      if (plan && !this.priceOf(plan, this.period())) this.period.set('MONTHLY');
    });
  }

  priceOf(plan: Plan | null, period: BillingPeriod): PlanPeriodPrice | null {
    if (!plan) return null;
    return (plan.prices ?? []).find((pp) => pp.period === period) ?? null;
  }

  pickPlan(p: Plan): void {
    this.planId.set(p.id);
  }

  pickPeriod(b: BillingPeriod): void {
    if (this.selectedPlan() && !this.priceOf(this.selectedPlan(), b)) return;
    this.period.set(b);
  }

  toggleSpecial(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.special.set(on);
    if (!on) {
      this.customPrice.set(null);
      this.customNote.set('');
    }
  }

  onPrice(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.customPrice.set(raw === '' ? null : Number(raw));
  }

  onNote(event: Event): void {
    this.customNote.set((event.target as HTMLTextAreaElement).value);
  }
}
