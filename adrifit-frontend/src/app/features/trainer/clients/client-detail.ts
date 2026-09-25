import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';
import { ClientService } from '../../../core/services/client.service';
import { ReportService } from '../../../core/services/report.service';
import { PlanService } from '../../../core/services/plan.service';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { DietService } from '../../../core/services/diet.service';
import { AnalysisService } from '../../../core/services/analysis.service';
import { NotifyService } from '../../../core/services/notify.service';
import { PaymentService } from '../../../core/services/payment.service';
import { WorkoutLogService } from '../../../core/services/workout-log.service';
import { PhotoService } from '../../../core/services/photo.service';
import { Client } from '../../../shared/models/client.model';
import { WeeklyReport } from '../../../shared/models/report.model';
import { Plan } from '../../../shared/models/plan.model';
import { SUBSCRIPTION_STATUS_LABEL, Subscription, SubscriptionStatus } from '../../../shared/models/subscription.model';
import { ClientWorkout, Workout } from '../../../shared/models/workout.model';
import { ClientDiet, DietSummary } from '../../../shared/models/diet.model';
import { ClientAnalysis } from '../../../shared/models/analysis.model';
import { PAYMENT_METHOD_LABEL, PAYMENT_STATUS_LABEL, Payment, PaymentStatus } from '../../../shared/models/payment.model';
import { ExerciseProgress, WorkoutLog } from '../../../shared/models/workout-log.model';
import { POSE_LABEL, ProgressPhoto } from '../../../shared/models/photo.model';
import { ChartPoint, LineChart } from '../../../shared/components/line-chart';
import { SecureImg } from '../../../shared/components/secure-img';
import { openBlob, saveBlob } from '../../../shared/utils/download';

type DetailTab =
  | 'resumen' | 'suscripciones' | 'pagos' | 'rutinas' | 'entrenos' | 'dietas' | 'reportes'
  | 'analiticas' | 'progreso' | 'fotos' | 'notas';

interface PhotoGroup {
  date: string;
  photos: ProgressPhoto[];
}

@Component({
  selector: 'app-client-detail',
  imports: [RouterLink, DatePipe, DecimalPipe, FormsModule, MatIconModule, LineChart, SecureImg],
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
  private readonly notify = inject(NotifyService);
  private readonly paymentService = inject(PaymentService);
  private readonly workoutLogService = inject(WorkoutLogService);
  private readonly photoService = inject(PhotoService);

  readonly subStatusLabel = SUBSCRIPTION_STATUS_LABEL;
  readonly paymentStatusLabel = PAYMENT_STATUS_LABEL;
  readonly paymentMethodLabel = PAYMENT_METHOD_LABEL;
  readonly poseLabel = POSE_LABEL;

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
  readonly updatingStatus = signal(false);

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
  readonly activeTab = signal<DetailTab>('resumen');
  readonly notes = signal<string>('');
  readonly notesSaved = signal(false);
  readonly savingNotes = signal(false);

  // Edit profile
  readonly editOpen = signal(false);
  readonly savingEdit = signal(false);
  editForm = { firstName: '', lastName: '', phone: '', birthDate: '', objective: '' };

  // Reset password
  readonly resettingPassword = signal(false);
  readonly tempPassword = signal<string | null>(null);

  // Payments
  readonly payments = signal<Payment[]>([]);
  readonly paymentsLoading = signal(false);
  readonly paymentsError = signal(false);
  readonly paymentBusyId = signal<number | null>(null);
  readonly pendingPaymentsCount = computed(() => this.payments().filter((p) => p.status === 'PENDING').length);

  // Workout logs
  readonly workoutLogs = signal<WorkoutLog[]>([]);
  readonly logsLoading = signal(false);
  readonly logsError = signal(false);
  readonly expandedLogId = signal<number | null>(null);
  readonly exerciseProgress = signal<ExerciseProgress[]>([]);
  readonly selectedExercise = signal<string | null>(null);
  readonly selectedProgress = computed(
    () => this.exerciseProgress().find((e) => e.exerciseName === this.selectedExercise()) ?? null,
  );
  readonly maxWeightPoints = computed<ChartPoint[]>(() =>
    (this.selectedProgress()?.points ?? []).map((p) => ({ label: this.shortDate(p.date), value: p.maxWeightKg })),
  );
  readonly oneRmPoints = computed<ChartPoint[]>(() =>
    (this.selectedProgress()?.points ?? []).map((p) => ({ label: this.shortDate(p.date), value: p.estimatedOneRepMaxKg })),
  );

  // Photos
  readonly photos = signal<ProgressPhoto[]>([]);
  readonly photosLoading = signal(false);
  readonly photosError = signal(false);
  readonly viewingPhoto = signal<ProgressPhoto | null>(null);
  readonly commentDrafts = signal<Record<number, string>>({});
  readonly savingCommentId = signal<number | null>(null);
  readonly photoGroups = computed<PhotoGroup[]>(() => {
    const groups: PhotoGroup[] = [];
    const sorted = [...this.photos()].sort((a, b) => b.takenOn.localeCompare(a.takenOn));
    for (const p of sorted) {
      const last = groups[groups.length - 1];
      if (last && last.date === p.takenOn) last.photos.push(p);
      else groups.push({ date: p.takenOn, photos: [p] });
    }
    return groups;
  });

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
      energyLevel: r.energyLevel ?? null,
      dietAdherence: r.dietAdherence ?? null,
      trainingAdherence: r.trainingAdherence ?? null,
      hasFeedback: !!r.coachFeedback,
    }));
  });

  readonly weightPoints = computed<ChartPoint[]>(() =>
    this.progressChartData().map((r) => ({ label: this.shortDate(r.date), value: r.weight })),
  );
  readonly waistPoints = computed<ChartPoint[]>(() =>
    this.progressChartData().map((r) => ({ label: this.shortDate(r.date), value: r.waist })),
  );
  readonly bodyFatPoints = computed<ChartPoint[]>(() =>
    this.progressChartData().map((r) => ({ label: this.shortDate(r.date), value: r.bodyFat })),
  );

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    const clientId = Number(this.id());
    this.loading.set(true);
    this.clientService.findById(clientId).subscribe({
      next: (c) => {
        this.client.set(c);
        this.notes.set(c.notes ?? '');
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
    this.loadPayments();
    this.loadWorkoutLogs();
    this.loadPhotos();
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
    this.analysisService.downloadContent(a.id, 'inline', 'trainer').subscribe({
      next: (blob) => openBlob(blob),
      error: (err) => this.notify.error(err, 'No se pudo abrir la analítica.'),
    });
  }

  downloadAnalysis(a: ClientAnalysis): void {
    this.analysisService.downloadContent(a.id, 'attachment', 'trainer').subscribe({
      next: (blob) => saveBlob(blob, a.originalFileName || `analitica-${a.id}.pdf`),
      error: (err) => this.notify.error(err, 'No se pudo descargar la analítica.'),
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
        this.notify.success('Analítica marcada como revisada.');
      },
      error: (err) => {
        this.savingReview.set(false);
        this.notify.error(err, 'No se pudo marcar como revisada.');
      },
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
      error: (err) => {
        this.savingDiet.set(false);
        this.dietMessage.set('No se pudo asignar la dieta.');
        this.notify.error(err, 'No se pudo asignar la dieta.');
      },
    });
  }

  exportDietPdf(): void {
    const clientId = Number(this.id());
    this.dietService.downloadClientPdf(clientId).subscribe({
      next: (blob) => saveBlob(blob, 'dieta-cliente.pdf'),
      error: (err) => this.notify.error(err, 'No se pudo descargar el PDF de la dieta.'),
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
        this.loadPayments();
      },
      error: (err) => {
        this.savingPlan.set(false);
        this.notify.error(err, 'No se pudo asignar el plan.');
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
      error: (err) => {
        this.savingWorkout.set(false);
        this.notify.error(err, 'No se pudo asignar la rutina.');
      },
    });
  }

  exportPdf(): void {
    const cw = this.clientWorkout();
    if (!cw) return;
    this.workoutService.downloadPdf(cw.workout.id, Number(this.id())).subscribe({
      next: (blob) => saveBlob(blob, `rutina-${cw.workout.name.replace(/\s+/g, '-').toLowerCase()}.pdf`),
      error: (err) => this.notify.error(err, 'No se pudo generar el PDF de la rutina.'),
    });
  }

  // ── Notes ─────────────────────────────────────────────────────────────

  saveNotes(): void {
    const c = this.client();
    if (!c || this.savingNotes()) return;
    this.savingNotes.set(true);
    this.clientService
      .update(c.id, {
        firstName: c.firstName,
        lastName: c.lastName,
        phone: c.phone,
        birthDate: c.birthDate,
        objective: c.objective,
        notes: this.notes(),
      })
      .subscribe({
        next: (updated) => {
          this.client.set(updated);
          this.notes.set(updated.notes ?? '');
          this.savingNotes.set(false);
          this.notesSaved.set(true);
          this.notify.success('Notas guardadas.');
          setTimeout(() => this.notesSaved.set(false), 2500);
        },
        error: (err) => {
          this.savingNotes.set(false);
          this.notify.error(err, 'No se pudieron guardar las notas.');
        },
      });
  }

  // ── Edit profile ──────────────────────────────────────────────────────

  openEdit(): void {
    const c = this.client();
    if (!c) return;
    this.editForm = {
      firstName: c.firstName,
      lastName: c.lastName,
      phone: c.phone ?? '',
      birthDate: c.birthDate ?? '',
      objective: c.objective ?? '',
    };
    this.editOpen.set(true);
  }

  saveEdit(): void {
    const c = this.client();
    if (!c) return;
    const f = this.editForm;
    if (!f.firstName.trim() || !f.lastName.trim()) {
      this.notify.error('Nombre y apellidos son obligatorios.');
      return;
    }
    this.savingEdit.set(true);
    this.clientService
      .update(c.id, {
        firstName: f.firstName.trim(),
        lastName: f.lastName.trim(),
        phone: f.phone.trim() || undefined,
        birthDate: f.birthDate || undefined,
        objective: f.objective.trim() || undefined,
        notes: c.notes,
      })
      .subscribe({
        next: (updated) => {
          this.client.set(updated);
          this.savingEdit.set(false);
          this.editOpen.set(false);
          this.notify.success('Datos del cliente actualizados.');
        },
        error: (err) => {
          this.savingEdit.set(false);
          this.notify.error(err, 'No se pudieron guardar los cambios.');
        },
      });
  }

  // ── Reset password ────────────────────────────────────────────────────

  resetPassword(): void {
    const c = this.client();
    if (!c) return;
    const ok = confirm(
      `¿Restablecer la contraseña de ${c.firstName} ${c.lastName}? Se generará una contraseña temporal que tendrá que cambiar al entrar.`,
    );
    if (!ok) return;
    this.resettingPassword.set(true);
    this.clientService.resetPassword(c.id).subscribe({
      next: (r) => {
        this.resettingPassword.set(false);
        this.tempPassword.set(r.temporaryPassword);
      },
      error: (err) => {
        this.resettingPassword.set(false);
        this.notify.error(err, 'No se pudo restablecer la contraseña.');
      },
    });
  }

  copyTempPassword(): void {
    const pwd = this.tempPassword();
    if (!pwd || !navigator.clipboard) return;
    navigator.clipboard.writeText(pwd).then(
      () => this.notify.success('Contraseña copiada al portapapeles.'),
      () => this.notify.error('No se pudo copiar. Cópiala manualmente.'),
    );
  }

  // ── Subscription status ───────────────────────────────────────────────

  subStatusBadge(status: SubscriptionStatus | undefined): string {
    return status === 'ACTIVE' ? 'green' : status === 'PAUSED' ? 'orange' : 'gray';
  }

  changeSubscriptionStatus(status: SubscriptionStatus): void {
    const messages: Record<SubscriptionStatus, string> = {
      PAUSED: '¿Pausar la suscripción de este cliente? No se generarán cobros mientras esté pausada.',
      ACTIVE: '¿Reactivar la suscripción de este cliente?',
      CANCELLED: '¿Cancelar la suscripción de este cliente? Su plan actual dejará de estar activo.',
    };
    if (!confirm(messages[status])) return;
    const clientId = Number(this.id());
    this.updatingStatus.set(true);
    this.subscriptionService.updateStatus(clientId, status).subscribe({
      next: (s) => {
        this.updatingStatus.set(false);
        this.subscription.set(s.status === 'CANCELLED' ? null : s);
        this.notify.success(`Suscripción ${SUBSCRIPTION_STATUS_LABEL[s.status].toLowerCase()}.`);
        this.loadSubscription(clientId);
      },
      error: (err) => {
        this.updatingStatus.set(false);
        this.notify.error(err, 'No se pudo cambiar el estado de la suscripción.');
      },
    });
  }

  // ── Payments ──────────────────────────────────────────────────────────

  loadPayments(): void {
    this.paymentsLoading.set(true);
    this.paymentsError.set(false);
    this.paymentService.findForClient(Number(this.id())).subscribe({
      next: (p) => {
        this.payments.set(p);
        this.paymentsLoading.set(false);
      },
      error: () => {
        this.paymentsError.set(true);
        this.paymentsLoading.set(false);
      },
    });
  }

  paymentBadge(status: PaymentStatus, overdue: boolean): string {
    if (status === 'PENDING') return overdue ? 'red' : 'orange';
    if (status === 'PAID') return 'green';
    if (status === 'REFUNDED') return 'violet';
    return 'gray';
  }

  markCash(p: Payment): void {
    if (!confirm(`¿Registrar el cobro en efectivo de ${p.amount.toFixed(2)} € (${p.concept})?`)) return;
    this.runPaymentAction(p, this.paymentService.markCashPaid(p.id), 'Cobro en efectivo registrado.');
  }

  cancelPayment(p: Payment): void {
    if (!confirm(`¿Anular el cobro "${p.concept}"?`)) return;
    this.runPaymentAction(p, this.paymentService.cancel(p.id), 'Cobro anulado.');
  }

  refundPayment(p: Payment): void {
    if (!confirm(`¿Devolver ${p.amount.toFixed(2)} € al cliente? (modo prueba, no se mueve dinero real)`)) return;
    this.runPaymentAction(p, this.paymentService.refund(p.id), 'Pago devuelto.');
  }

  private runPaymentAction(p: Payment, req: Observable<Payment>, ok: string): void {
    this.paymentBusyId.set(p.id);
    req.subscribe({
      next: (updated) => {
        this.paymentBusyId.set(null);
        this.payments.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.notify.success(ok);
      },
      error: (err) => {
        this.paymentBusyId.set(null);
        this.notify.error(err, 'No se pudo completar la operación.');
      },
    });
  }

  // ── Workout logs ──────────────────────────────────────────────────────

  loadWorkoutLogs(): void {
    const clientId = Number(this.id());
    this.logsLoading.set(true);
    this.logsError.set(false);
    this.workoutLogService.findForClient(clientId).subscribe({
      next: (logs) => {
        this.workoutLogs.set(logs);
        this.logsLoading.set(false);
      },
      error: () => {
        this.logsError.set(true);
        this.logsLoading.set(false);
      },
    });
    this.workoutLogService.progressForClient(clientId).subscribe({
      next: (prog) => {
        const sorted = [...prog].sort((a, b) => a.exerciseName.localeCompare(b.exerciseName));
        this.exerciseProgress.set(sorted);
        if (!this.selectedExercise() && sorted.length > 0) {
          const best = [...sorted].sort((a, b) => b.points.length - a.points.length)[0];
          this.selectedExercise.set(best.exerciseName);
        }
      },
      error: () => this.exerciseProgress.set([]),
    });
  }

  toggleLog(id: number): void {
    this.expandedLogId.set(this.expandedLogId() === id ? null : id);
  }

  // ── Photos ────────────────────────────────────────────────────────────

  loadPhotos(): void {
    this.photosLoading.set(true);
    this.photosError.set(false);
    this.photoService.findForClient(Number(this.id())).subscribe({
      next: (list) => {
        this.photos.set(list);
        const drafts: Record<number, string> = {};
        for (const p of list) drafts[p.id] = p.trainerComment ?? '';
        this.commentDrafts.set(drafts);
        this.photosLoading.set(false);
      },
      error: () => {
        this.photosError.set(true);
        this.photosLoading.set(false);
      },
    });
  }

  commentDraft(id: number): string {
    return this.commentDrafts()[id] ?? '';
  }

  setCommentDraft(id: number, value: string): void {
    this.commentDrafts.update((d) => ({ ...d, [id]: value }));
  }

  commentChanged(photo: ProgressPhoto): boolean {
    return (this.commentDrafts()[photo.id] ?? '').trim() !== (photo.trainerComment ?? '').trim();
  }

  saveComment(photo: ProgressPhoto): void {
    const comment = (this.commentDrafts()[photo.id] ?? '').trim();
    this.savingCommentId.set(photo.id);
    this.photoService.comment(photo.id, comment).subscribe({
      next: (updated) => {
        this.savingCommentId.set(null);
        this.photos.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.setCommentDraft(updated.id, updated.trainerComment ?? '');
        if (this.viewingPhoto()?.id === updated.id) this.viewingPhoto.set(updated);
        this.notify.success('Comentario guardado.');
      },
      error: (err) => {
        this.savingCommentId.set(null);
        this.notify.error(err, 'No se pudo guardar el comentario.');
      },
    });
  }

  // ── Misc ──────────────────────────────────────────────────────────────

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
      error: (err) => {
        this.uploadingPhoto.set(false);
        this.notify.error(err, 'No se pudo subir la foto.');
      },
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

  private shortDate(iso: string): string {
    const d = new Date(iso.length === 10 ? iso + 'T00:00:00' : iso);
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  get initials(): string {
    const c = this.client();
    return c ? (c.firstName[0] ?? '') + (c.lastName[0] ?? '') : '';
  }
}
