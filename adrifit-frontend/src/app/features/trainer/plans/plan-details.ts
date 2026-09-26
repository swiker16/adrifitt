import { Component, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../../core/services/plan.service';
import { BILLING_PERIOD_LABEL, BILLING_PERIOD_SUFFIX, Plan, splitFeature } from '../../../shared/models/plan.model';

interface FeatureFlag {
  label: string;
  enabled: boolean;
  icon: string;
}

@Component({
  selector: 'app-plan-details',
  imports: [RouterLink, DecimalPipe, MatIconModule],
  templateUrl: './plan-details.html',
  styleUrl: './plan-details.scss',
})
export class PlanDetails {
  private readonly planService = inject(PlanService);

  readonly id = input.required<string>();

  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;

  readonly plan = signal<Plan | null>(null);
  readonly featureItems = computed(() => (this.plan()?.features ?? []).map(splitFeature));
  readonly loading = signal(true);
  readonly error = signal(false);

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    this.planService.findById(Number(this.id())).subscribe({
      next: (p) => {
        this.plan.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  features(): FeatureFlag[] {
    const p = this.plan();
    if (!p) return [];
    return [
      { label: 'Chat con el coach', enabled: p.messagingEnabled, icon: 'chat' },
      { label: 'Analíticas de evolución', enabled: p.analyticsEnabled, icon: 'insights' },
      { label: 'Exportación a PDF', enabled: p.pdfExportEnabled, icon: 'picture_as_pdf' },
      { label: 'Soporte prioritario', enabled: p.prioritySupport, icon: 'support_agent' },
    ];
  }
}
