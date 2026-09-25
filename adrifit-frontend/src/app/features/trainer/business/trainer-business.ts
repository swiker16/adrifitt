import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DashboardService } from '../../../core/services/dashboard.service';
import { BarChart, BarPoint } from '../../../shared/components/bar-chart';
import { BusinessOverview, MethodAmount } from '../../../shared/models/business.model';
import { PAYMENT_METHOD_LABEL } from '../../../shared/models/payment.model';

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

@Component({
  selector: 'app-trainer-business',
  imports: [MatIconModule, DecimalPipe, BarChart],
  templateUrl: './trainer-business.html',
  styleUrl: './trainer-business.scss',
})
export class TrainerBusiness {
  private readonly dashboardService = inject(DashboardService);

  readonly methodLabel = PAYMENT_METHOD_LABEL;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly data = signal<BusinessOverview | null>(null);

  /** % change of this month's revenue vs last month (null when last month had no revenue). */
  readonly monthDelta = computed<number | null>(() => {
    const d = this.data();
    if (!d || !d.revenueLastMonth) return null;
    return ((d.revenueThisMonth - d.revenueLastMonth) / d.revenueLastMonth) * 100;
  });

  readonly revenuePoints = computed<BarPoint[]>(() =>
    (this.data()?.revenueByMonth ?? []).map((m) => ({ label: this.monthLabel(m.month), value: m.amount })),
  );

  readonly newClientsPoints = computed<BarPoint[]>(() =>
    (this.data()?.newClientsByMonth ?? []).map((m) => ({ label: this.monthLabel(m.month), value: m.count })),
  );

  readonly plans = computed(() => {
    const d = this.data();
    if (!d) return [];
    const total = d.plans.reduce((acc, p) => acc + p.mrr, 0);
    return [...d.plans]
      .sort((a, b) => b.mrr - a.mrr)
      .map((p) => ({ ...p, share: total > 0 ? (p.mrr / total) * 100 : 0 }));
  });

  readonly methods = computed<(MethodAmount & { share: number })[]>(() => {
    const d = this.data();
    const order: MethodAmount['method'][] = ['CARD', 'BIZUM', 'CASH'];
    const list = order.map(
      (m) => d?.revenueByMethod.find((x) => x.method === m) ?? { method: m, amount: 0, payments: 0 },
    );
    const total = list.reduce((acc, m) => acc + m.amount, 0);
    return list.map((m) => ({ ...m, share: total > 0 ? (m.amount / total) * 100 : 0 }));
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.dashboardService.getBusinessOverview().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  monthLabel(yyyyMM: string): string {
    const m = Number(yyyyMM?.split('-')[1]);
    return MONTHS[m - 1] ?? yyyyMM;
  }

  methodIcon(m: MethodAmount['method']): string {
    return { CARD: 'credit_card', BIZUM: 'smartphone', CASH: 'payments' }[m];
  }

  abs(n: number): number {
    return Math.abs(n);
  }
}
