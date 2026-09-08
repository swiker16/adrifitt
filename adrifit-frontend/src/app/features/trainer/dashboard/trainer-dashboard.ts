import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DashboardService } from '../../../core/services/dashboard.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TrainerDashboard as TrainerDashboardData, UpcomingRenewal, PendingReview } from '../../../shared/models/dashboard.model';

export interface CalendarDay {
  date: Date;
  dayNum: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  payments: UpcomingRenewal[];
  reviews: PendingReview[];
}

@Component({
  selector: 'app-trainer-dashboard',
  imports: [RouterLink, MatIconModule, DatePipe, DecimalPipe, TitleCasePipe],
  templateUrl: './trainer-dashboard.html',
  styleUrl: './trainer-dashboard.scss',
})
export class TrainerDashboard {
  private readonly dashboardService = inject(DashboardService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly username = computed(() => this.auth.user()?.username ?? 'Entrenador');
  readonly today = new Date();

  readonly loading = signal(true);
  readonly data = signal<TrainerDashboardData | null>(null);

  readonly clientsPerPlan = computed(() => this.data()?.clientsPerPlan ?? []);
  readonly totalPlanClients = computed(() => this.clientsPerPlan().reduce((s, p) => s + p.clients, 0));

  readonly overdueReviews = computed((): PendingReview[] => {
    const todayIso = this.toIso(new Date());
    return (this.data()?.pendingReviews ?? []).filter(r => r.nextReviewDate <= todayIso);
  });

  private toIso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  readonly donutSegments = computed(() => {
    const plans = this.clientsPerPlan();
    const total = this.totalPlanClients();
    if (total === 0) return [];
    const colors = ['#FF7A1A', '#3B82F6', '#8B5CF6', '#10B981', '#F59E0B'];
    let offset = 0;
    const circumference = 2 * Math.PI * 54;
    return plans.map((p, i) => {
      const pct = p.clients / total;
      const dash = pct * circumference;
      const seg = { plan: p.planName, clients: p.clients, pct: Math.round(pct * 100), color: colors[i % colors.length], dash, offset };
      offset += dash;
      return seg;
    });
  });

  readonly calendarMonth = signal(new Date());

  readonly calendarTitle = computed(() => {
    const d = this.calendarMonth();
    return d.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  });

  readonly calendarDays = computed((): CalendarDay[] => {
    const base = this.calendarMonth();
    const year = base.getFullYear();
    const month = base.getMonth();
    const today = new Date();
    const renewals = this.data()?.upcomingRenewals ?? [];
    const reviews = this.data()?.pendingReviews ?? [];

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDow = (firstDay.getDay() + 6) % 7;
    const days: CalendarDay[] = [];

    for (let i = 0; i < startDow; i++) {
      const d = new Date(year, month, -(startDow - 1 - i));
      days.push({ date: d, dayNum: d.getDate(), isCurrentMonth: false, isToday: false, payments: [], reviews: [] });
    }
    for (let d = 1; d <= lastDay.getDate(); d++) {
      const date = new Date(year, month, d);
      const iso = `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
      const isToday = date.toDateString() === today.toDateString();
      const payments = renewals.filter(r => r.renewalDate.startsWith(iso));
      const dayReviews = reviews.filter(r => r.nextReviewDate.startsWith(iso));
      days.push({ date, dayNum: d, isCurrentMonth: true, isToday, payments, reviews: dayReviews });
    }
    const remaining = 7 - (days.length % 7 === 0 ? 7 : days.length % 7);
    for (let i = 1; i <= remaining; i++) {
      const d = new Date(year, month + 1, i);
      days.push({ date: d, dayNum: i, isCurrentMonth: false, isToday: false, payments: [], reviews: [] });
    }
    return days;
  });

  prevMonth(): void {
    const d = this.calendarMonth();
    this.calendarMonth.set(new Date(d.getFullYear(), d.getMonth() - 1, 1));
  }
  nextMonth(): void {
    const d = this.calendarMonth();
    this.calendarMonth.set(new Date(d.getFullYear(), d.getMonth() + 1, 1));
  }
  resetMonth(): void { this.calendarMonth.set(new Date()); }

  paymentTitle(day: CalendarDay): string {
    return day.payments.length + ' cobro(s): ' + day.payments.map(p => p.clientFirstName + ' ' + p.clientLastName).join(', ');
  }
  reviewTitle(day: CalendarDay): string {
    return day.reviews.length + ' revisión(es): ' + day.reviews.map(r => r.clientFirstName + ' ' + r.clientLastName).join(', ');
  }

  readonly ingresos = computed(() => {
    const d = this.data();
    if (!d) return 0;
    return d.upcomingRenewals.reduce((sum, r) => sum + (r.amount ?? 0), 0);
  });

  constructor() {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.dashboardService.getTrainerDashboard().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); },
    });
  }

  initials(first: string, last: string): string {
    return (first?.[0] ?? '') + (last?.[0] ?? '');
  }

  timeAgo(dateStr: string): string {
    const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 60000);
    if (diff < 1) return 'Ahora mismo';
    if (diff < 60) return `Hace ${diff} min`;
    const h = Math.floor(diff / 60);
    if (h < 24) return `Hace ${h}h`;
    const d = Math.floor(h / 24);
    if (d === 1) return 'Ayer';
    return `Hace ${d} días`;
  }

  activityIcon(type: string): string {
    const icons: Record<string, string> = {
      CLIENT_CREATED: 'person_add',
      REPORT_SUBMITTED: 'assignment',
      WORKOUT_ASSIGNED: 'fitness_center',
      DIET_ASSIGNED: 'restaurant',
      PLAN_CHANGED: 'swap_horiz',
    };
    return icons[type] ?? 'circle';
  }

  activityColor(type: string): string {
    const colors: Record<string, string> = {
      CLIENT_CREATED: 'orange',
      REPORT_SUBMITTED: 'blue',
      WORKOUT_ASSIGNED: 'green',
      DIET_ASSIGNED: 'violet',
      PLAN_CHANGED: 'red',
    };
    return colors[type] ?? 'gray';
  }

  goToClient(id: number): void {
    this.router.navigate(['/trainer/clients', id]);
  }
}
