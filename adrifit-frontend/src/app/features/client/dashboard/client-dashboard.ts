import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { ReportService } from '../../../core/services/report.service';
import { Subscription } from '../../../shared/models/subscription.model';
import { ClientWorkout } from '../../../shared/models/workout.model';
import { WeeklyReport } from '../../../shared/models/report.model';

interface FeatureFlag {
  label: string;
  enabled: boolean;
  icon: string;
}

@Component({
  selector: 'app-client-dashboard',
  imports: [RouterLink, DatePipe, DecimalPipe, MatIconModule],
  templateUrl: './client-dashboard.html',
  styleUrl: './client-dashboard.scss',
})
export class ClientDashboard {
  private readonly auth = inject(AuthService);
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly workoutService = inject(WorkoutService);
  private readonly reportService = inject(ReportService);

  readonly username = computed(() => this.auth.user()?.username ?? 'cliente');
  readonly subscription = signal<Subscription | null>(null);
  readonly subLoaded = signal(false);
  readonly clientWorkout = signal<ClientWorkout | null>(null);
  readonly workoutLoaded = signal(false);
  readonly latestReport = signal<WeeklyReport | null>(null);
  readonly reportLoaded = signal(false);

  readonly features = computed<FeatureFlag[]>(() => {
    const p = this.subscription()?.plan;
    if (!p) return [];
    return [
      { label: 'Mensajería', enabled: p.messagingEnabled, icon: 'chat' },
      { label: 'Analíticas', enabled: p.analyticsEnabled, icon: 'insights' },
      { label: 'Exportar PDF', enabled: p.pdfExportEnabled, icon: 'picture_as_pdf' },
      { label: 'Soporte prioritario', enabled: p.prioritySupport, icon: 'support_agent' },
    ];
  });

  readonly nextReviewDate = computed<Date | null>(() => {
    const sub = this.subscription();
    const report = this.latestReport();
    if (!sub) return null;
    const freqDays = sub.plan?.reviewFrequencyDays;
    if (!freqDays) return null;
    const ref = report ? new Date(report.createdAt) : new Date(sub.startDate);
    ref.setDate(ref.getDate() + freqDays);
    return ref;
  });

  constructor() {
    this.subscriptionService.getMine().subscribe({
      next: (s) => { this.subscription.set(s); this.subLoaded.set(true); },
      error: () => this.subLoaded.set(true),
    });
    this.workoutService.getMyWorkout().subscribe({
      next: (cw) => { this.clientWorkout.set(cw); this.workoutLoaded.set(true); },
      error: () => this.workoutLoaded.set(true),
    });
    this.reportService.findMine().subscribe({
      next: (reports) => {
        this.latestReport.set(reports.length > 0 ? reports[0] : null);
        this.reportLoaded.set(true);
      },
      error: () => this.reportLoaded.set(true),
    });
  }
}
