import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { DecimalPipe } from '@angular/common';
import { LeadService } from '../../core/services/lead.service';
import { PlanService } from '../../core/services/plan.service';
import { Q_LABELS, Questionnaire, QuestionnaireInfo } from '../../shared/models/lead.model';
import { BILLING_PERIOD_LABEL, BillingPeriod, Plan } from '../../shared/models/plan.model';
import { apiErrorMessage } from '../../shared/utils/download';
import { PublicFrame } from './public-frame';

type Answers = Partial<Questionnaire> & { hasInjuries: boolean; hasMedicalConditions: boolean; healthConsent: boolean };

const STEPS = ['Sobre ti', 'Tu objetivo', 'Salud', 'Entrenamiento y hábitos', 'Tu plan'];

/** Questionnaire sent by the trainer to a prospect (public link with a personal token). */
@Component({
  selector: 'app-questionnaire-page',
  imports: [FormsModule, RouterLink, MatIconModule, DecimalPipe, PublicFrame],
  templateUrl: './questionnaire-page.html',
  styleUrl: './questionnaire-page.scss',
})
export class QuestionnairePage implements OnInit {
  private readonly leads = inject(LeadService);
  private readonly planService = inject(PlanService);
  private readonly route = inject(ActivatedRoute);

  readonly L = Q_LABELS;
  readonly steps = STEPS;
  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  readonly info = signal<QuestionnaireInfo | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly plans = signal<Plan[]>([]);
  readonly step = signal(0);
  readonly errors = signal<string[]>([]);
  readonly sending = signal(false);
  readonly done = signal(false);
  readonly progress = computed(() => Math.round(((this.step() + 1) / STEPS.length) * 100));

  q: Answers = { hasInjuries: false, hasMedicalConditions: false, healthConsent: false, billingPeriod: 'MONTHLY' };

  ngOnInit(): void {
    this.leads.questionnaireInfo(this.token).subscribe({
      next: (info) => {
        this.info.set(info);
        if (info.state === 'OPEN') {
          this.restoreDraft();
          if (!this.q.planId && info.preferredPlanId) this.q.planId = info.preferredPlanId;
        }
      },
      error: (err) => this.loadError.set(apiErrorMessage(err, 'No se pudo abrir el cuestionario. Revisa tu conexión.')),
    });
    this.planService.findAll(true).subscribe({ next: (p) => this.plans.set(p), error: () => undefined });
  }

  options<T extends string>(labels: Record<T, string>): [T, string][] {
    return Object.entries(labels) as [T, string][];
  }

  selectedPlan(): Plan | undefined {
    return this.plans().find((p) => p.id === this.q.planId);
  }

  periodsOf(plan: Plan | undefined): { period: BillingPeriod; price: number; savingPercent: number }[] {
    return plan?.prices ?? [];
  }

  next(): void {
    const problems = this.validate(this.step());
    this.errors.set(problems);
    if (problems.length) return;
    this.saveDraft();
    this.step.update((s) => Math.min(s + 1, STEPS.length - 1));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  back(): void {
    this.errors.set([]);
    this.step.update((s) => Math.max(s - 1, 0));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  submit(): void {
    for (let i = 0; i < STEPS.length; i++) {
      const problems = this.validate(i);
      if (problems.length) {
        this.step.set(i);
        this.errors.set(problems);
        return;
      }
    }
    this.sending.set(true);
    this.errors.set([]);
    this.leads.submitQuestionnaire(this.token, this.q as Questionnaire).subscribe({
      next: () => {
        this.sending.set(false);
        this.done.set(true);
        this.clearDraft();
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (err) => {
        this.sending.set(false);
        this.errors.set([apiErrorMessage(err, 'No se pudo enviar el cuestionario. Inténtalo de nuevo.')]);
      },
    });
  }

  /** Errors shrink as soon as the person fixes each answer. */
  revalidate(): void {
    if (this.errors().length) this.errors.set(this.validate(this.step()));
  }

  saveDraft(): void {
    this.revalidate();
    try {
      localStorage.setItem(this.draftKey(), JSON.stringify(this.q));
    } catch {
      /* storage unavailable */
    }
  }

  private validate(step: number): string[] {
    const q = this.q;
    const e: string[] = [];
    if (step === 0) {
      if (!q.birthDate) e.push('Indica tu fecha de nacimiento.');
      if (!q.sex) e.push('Indica tu sexo.');
      if (!q.heightCm || q.heightCm < 100 || q.heightCm > 250) e.push('Indica tu altura en cm (100-250).');
      if (!q.weightKg || q.weightKg < 30 || q.weightKg > 300) e.push('Indica tu peso en kg (30-300).');
      if (!q.activityLevel) e.push('Elige tu nivel de actividad diaria.');
    } else if (step === 1) {
      if (!q.mainGoal) e.push('Elige tu objetivo principal.');
      if (!q.experience) e.push('Indica tu experiencia entrenando.');
    } else if (step === 2) {
      if (q.hasInjuries && !q.injuries?.trim()) e.push('Describe brevemente tus lesiones o molestias.');
      if (q.hasMedicalConditions && !q.medicalConditions?.trim()) e.push('Describe brevemente tu condición médica.');
    } else if (step === 3) {
      if (!q.daysPerWeek) e.push('Indica cuántos días puedes entrenar.');
      if (!q.minutesPerSession) e.push('Indica cuánto tiempo tienes por sesión.');
      if (!q.trainingPlace) e.push('Indica dónde entrenarás.');
      if (!q.dietType) e.push('Indica tu tipo de alimentación.');
    } else if (step === 4) {
      if (!q.healthConsent) e.push('Necesitamos tu consentimiento para tratar tus datos de salud.');
    }
    return e;
  }

  private draftKey(): string {
    return 'adrifit_questionnaire_' + this.token.slice(0, 16);
  }

  private restoreDraft(): void {
    try {
      const raw = localStorage.getItem(this.draftKey());
      if (raw) this.q = { ...this.q, ...JSON.parse(raw), healthConsent: false };
    } catch {
      /* ignore */
    }
  }

  private clearDraft(): void {
    try {
      localStorage.removeItem(this.draftKey());
    } catch {
      /* ignore */
    }
  }
}
