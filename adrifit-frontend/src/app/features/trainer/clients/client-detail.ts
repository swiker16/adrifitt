import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ClientService } from '../../../core/services/client.service';
import { ReportService } from '../../../core/services/report.service';
import { PlanService } from '../../../core/services/plan.service';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { DietService } from '../../../core/services/diet.service';
import { AnalysisService } from '../../../core/services/analysis.service';
import { Client } from '../../../shared/models/client.model';
import { WeeklyReport } from '../../../shared/models/report.model';
import { Plan } from '../../../shared/models/plan.model';
import { Subscription } from '../../../shared/models/subscription.model';
import { ClientWorkout, Workout } from '../../../shared/models/workout.model';
import { ClientDiet, DietSummary } from '../../../shared/models/diet.model';
import { ClientAnalysis } from '../../../shared/models/analysis.model';

@Component({
  selector: 'app-client-detail',
  imports: [RouterLink, DatePipe, DecimalPipe, FormsModule, MatIconModule],
  templateUrl: './client-detail.html',
  styleUrl: './client-detail.scss',
})
export class ClientDetail {
  private readonly clientService = inject(ClientService);
  private readonly reportService = inject(ReportService);
  private readonly planService = inject(PlanService);
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly workoutService = inject(WorkoutService);
  private readonly dietService = inject(DietService);
  private readonly analysisService = inject(AnalysisService);

  // Bound from the :id route param via withComponentInputBinding().
  readonly id = input.required<string>();

  readonly client = signal<Client | null>(null);
  readonly reports = signal<WeeklyReport[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  readonly plans = signal<Plan[]>([]);
  readonly subscription = signal<Subscription | null>(null);
  readonly subscriptionHistory = signal<Subscription[]>([]);
  readonly selectedPlanId = signal<number | null>(null);
  readonly savingPlan = signal(false);
  readonly planMessage = signal<string | null>(null);

  readonly workouts = signal<Workout[]>([]);
  readonly clientWorkout = signal<ClientWorkout | null>(null);
  readonly workoutHistory = signal<ClientWorkout[]>([]);
  readonly selectedWorkoutId = signal<number | null>(null);
  readonly savingWorkout = signal(false);
  readonly workoutMessage = signal<string | null>(null);

  readonly diets = signal<DietSummary[]>([]);
  readonly clientDiet = signal<ClientDiet | null>(null);
  readonly dietHistory = signal<ClientDiet[]>([]);
  readonly selectedDietId = signal<number | null>(null);
  readonly savingDiet = signal(false);
  readonly dietMessage = signal<string | null>(null);
  readonly showAssignDiet = signal(false);

  readonly clientAnalyses = signal<ClientAnalysis[]>([]);
  readonly analysesLoading = signal(false);
  readonly reviewingAnalysis = signal<ClientAnalysis | null>(null);
  readonly reviewNote = signal('');
  readonly savingReview = signal(false);

  readonly showAssignPlan = signal(false);
  readonly showAssignWorkout = signal(false);
  readonly uploadingPhoto = signal(false);
  readonly activeTab = signal<'resumen' | 'suscripciones' | 'rutinas' | 'dietas' | 'reportes' | 'analiticas' | 'progreso' | 'notas'>('resumen');
  readonly notes = signal<string>('');
  readonly notesSaved = signal(false);

  readonly latestReport = computed(() => this.reports()[0] ?? null);
  readonly firstReport = computed(() => this.reports().length > 0 ? this.reports()[this.reports().length - 1] : null);

  readonly weightDelta = computed(() => {
    const l = this.latestReport(), f = this.firstReport();
    if (!l || !f || l === f) return null;
    return (l.weight - f.weight);
  });

  readonly waistDelta = computed(() => {
    const l = this.latestReport(), f = this.firstReport();
    if (!l?.waist || !f?.waist || l === f) return null;
    return (l.waist - f.waist);
  });

  readonly bodyFatDelta = computed(() => {
    const l = this.latestReport(), f = this.firstReport();
    if (!l?.bodyFat || !f?.bodyFat || l === f) return null;
    return (l.bodyFat - f.bodyFat);
  });

  readonly progressChartData = computed(() => {
    return [...this.reports()].reverse().map(r => ({
      date: r.createdAt,
      weight: r.weight,
      waist: r.waist ?? null,
      bodyFat: r.bodyFat ?? null,
    }));
  });

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    const clientId = Number(this.id());
    this.loading.set(true);
    this.clientService.findById(clientId).subscribe({
      next: (c) => {
        this.client.set(c);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
    this.reportService.findByClient(clientId).subscribe({
      next: (r) => this.reports.set(r),
      error: () => this.reports.set([]),
    });
    this.planService.findAll(true).subscribe({
      next: (p) => this.plans.set(p),
      error: () => this.plans.set([]),
    });
    this.workoutService.findAll().subscribe({
      next: (w) => this.workouts.set(w),
      error: () => this.workouts.set([]),
    });
    this.loadSubscription(clientId);
    this.loadClientWorkout(clientId);
    this.loadClientDiet(clientId);
    this.loadClientAnalyses(clientId);
    this.dietService.findAll().subscribe({
      next: (d) => this.diets.set(d.filter(x => x.active)),
      error: () => this.diets.set([]),
    });
  }

  private loadClientDiet(clientId: number): void {
    this.dietService.getActiveForClient(clientId).subscribe({
      next: (cd) => { this.clientDiet.set(cd); this.selectedDietId.set(cd.dietId); },
      error: () => this.clientDiet.set(null),
    });
    this.dietService.getHistoryForClient(clientId).subscribe({
      next: (h) => this.dietHistory.set(h),
      error: () => this.dietHistory.set([]),
    });
  }

  private loadClientAnalyses(clientId: number): void {
    this.analysesLoading.set(true);
    this.analysisService.getForClient(clientId).subscribe({
      next: (list) => { this.clientAnalyses.set(list); this.analysesLoading.set(false); },
      error: () => { this.analysesLoading.set(false); },
    });
  }

  openAnalysis(a: ClientAnalysis): void {
    this.analysisService.downloadContent(a.id, 'inline', 'trainer').subscribe(blob => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    });
  }

  downloadAnalysis(a: ClientAnalysis): void {
    this.analysisService.downloadContent(a.id, 'attachment', 'trainer').subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const el = document.createElement('a');
      el.href = url; el.download = a.originalFileName; el.click();
      URL.revokeObjectURL(url);
    });
  }

  submitReview(analysisId: number): void {
    this.savingReview.set(true);
    this.analysisService.review(analysisId, { trainerInternalNote: this.reviewNote() || undefined }).subscribe({
      next: (updated) => {
        this.clientAnalyses.update(list => list.map(a => a.id === analysisId ? updated : a));
        this.reviewingAnalysis.set(null);
        this.reviewNote.set('');
        this.savingReview.set(false);
      },
      error: () => this.savingReview.set(false),
    });
  }

  assignDiet(): void {
    const dietId = this.selectedDietId();
    if (!dietId) return;
    this.savingDiet.set(true);
    this.dietMessage.set(null);
    const clientId = Number(this.id());
    this.dietService.assignToClient(clientId, { dietId }).subscribe({
      next: (cd) => {
        this.clientDiet.set(cd);
        this.savingDiet.set(false);
        this.dietMessage.set('Dieta asignada correctamente.');
        this.showAssignDiet.set(false);
        this.loadClientDiet(clientId);
      },
      error: () => {
        this.savingDiet.set(false);
        this.dietMessage.set('No se pudo asignar la dieta.');
      },
    });
  }

  exportDietPdf(): void {
    const clientId = Number(this.id());
    this.dietService.downloadClientPdf(clientId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = 'dieta-cliente.pdf'; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => alert('No se pudo descargar el PDF de la dieta.'),
    });
  }

  private loadClientWorkout(clientId: number): void {
    this.workoutService.getActiveForClient(clientId).subscribe({
      next: (cw) => {
        this.clientWorkout.set(cw);
        this.selectedWorkoutId.set(cw.workout.id);
      },
      error: () => this.clientWorkout.set(null),
    });
    this.workoutService.getHistoryForClient(clientId).subscribe({
      next: (h) => this.workoutHistory.set(h),
      error: () => this.workoutHistory.set([]),
    });
  }

  private loadSubscription(clientId: number): void {
    this.subscriptionService.getActive(clientId).subscribe({
      next: (s) => {
        this.subscription.set(s);
        this.selectedPlanId.set(s.planId);
      },
      error: () => this.subscription.set(null),
    });
    this.subscriptionService.getHistory(clientId).subscribe({
      next: (h) => this.subscriptionHistory.set(h),
      error: () => this.subscriptionHistory.set([]),
    });
  }

  assignPlan(): void {
    const planId = Number(this.selectedPlanId());
    if (!planId) return;
    this.savingPlan.set(true);
    this.planMessage.set(null);
    const clientId = Number(this.id());
    this.subscriptionService.assignPlan(clientId, { planId }).subscribe({
      next: (s) => {
        this.subscription.set(s);
        this.savingPlan.set(false);
        this.planMessage.set('Plan asignado correctamente.');
        this.showAssignPlan.set(false);
        this.loadSubscription(clientId);
      },
      error: () => {
        this.savingPlan.set(false);
        this.planMessage.set('No se pudo asignar el plan.');
      },
    });
  }

  assignWorkout(): void {
    const workoutId = this.selectedWorkoutId();
    if (!workoutId) return;
    this.savingWorkout.set(true);
    this.workoutMessage.set(null);
    this.workoutService.assignToClient(Number(this.id()), { workoutId }).subscribe({
      next: (cw) => {
        this.clientWorkout.set(cw);
        this.savingWorkout.set(false);
        this.workoutMessage.set('Rutina asignada correctamente.');
        this.showAssignWorkout.set(false);
        this.loadClientWorkout(Number(this.id()));
      },
      error: () => {
        this.savingWorkout.set(false);
        this.workoutMessage.set('No se pudo asignar la rutina.');
      },
    });
  }

  exportPdf(): void {
    const cw = this.clientWorkout();
    if (!cw) return;
    this.workoutService.downloadPdf(cw.workout.id, Number(this.id())).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rutina-${cw.workout.name.replace(/\s+/g, '-').toLowerCase()}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  saveNotes(): void {
    this.notesSaved.set(true);
    setTimeout(() => this.notesSaved.set(false), 2500);
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadingPhoto.set(true);
    this.clientService.uploadPhoto(Number(this.id()), file).subscribe({
      next: (updated) => {
        this.client.set(updated);
        this.uploadingPhoto.set(false);
      },
      error: () => this.uploadingPhoto.set(false),
    });
    input.value = '';
  }

  deltaSign(val: number | null): string {
    if (val === null) return '';
    return val > 0 ? '+' : '';
  }

  deltaClass(val: number | null, lowerIsBetter = true): string {
    if (val === null || val === 0) return 'neutral';
    const improved = lowerIsBetter ? val < 0 : val > 0;
    return improved ? 'positive' : 'negative';
  }

  get initials(): string {
    const c = this.client();
    return c ? (c.firstName[0] ?? '') + (c.lastName[0] ?? '') : '';
  }
}
