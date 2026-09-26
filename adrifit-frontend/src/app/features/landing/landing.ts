import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { ThemeToggle } from '../../shared/components/theme-toggle';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../core/services/plan.service';
import {
  BILLING_PERIOD_LABEL,
  BILLING_PERIOD_SUFFIX,
  BILLING_PERIODS,
  BillingPeriod,
  Plan,
  PlanPeriodPrice,
  splitFeature,
} from '../../shared/models/plan.model';
import { TestimonialService } from '../../core/services/testimonial.service';
import { PublicTestimonial } from '../../shared/models/testimonial.model';

interface Feature {
  icon: string;
  tone: 'orange' | 'green' | 'blue' | 'violet';
  title: string;
  text: string;
  size?: 'wide' | 'tall';
}

interface Step {
  number: string;
  title: string;
  text: string;
}


@Component({
  selector: 'app-landing',
  imports: [RouterLink, MatIconModule, DecimalPipe, ThemeToggle],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private readonly planService = inject(PlanService);
  private readonly testimonialService = inject(TestimonialService);

  readonly apiPlans = signal<Plan[]>([]);
  readonly plansLoading = signal(true);

  /** Highlighted plan: the one called "Premium", otherwise the most expensive. */
  readonly featuredPlanId = computed(() => {
    const plans = this.apiPlans();
    if (plans.length === 0) return null;
    const premium = plans.find((p) => p.name.toLowerCase().includes('premium'));
    if (premium) return premium.id;
    return [...plans].sort((a, b) => b.monthlyPrice - a.monthlyPrice)[0].id;
  });

  // ── Pricing period toggle ────────────────────────────────────────────────
  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;
  readonly period = signal<BillingPeriod>('MONTHLY');

  /** Periods offered by at least one plan (always includes monthly). */
  readonly periods = computed<BillingPeriod[]>(() => {
    const offered = new Set<BillingPeriod>(['MONTHLY']);
    this.apiPlans().forEach((p) => (p.prices ?? []).forEach((pp) => offered.add(pp.period)));
    return BILLING_PERIODS.filter((b) => offered.has(b));
  });

  /** Best saving % of each period across plans (for the toggle hint). */
  readonly bestSaving = computed<Partial<Record<BillingPeriod, number>>>(() => {
    const out: Partial<Record<BillingPeriod, number>> = {};
    this.apiPlans().forEach((p) =>
      (p.prices ?? []).forEach((pp) => {
        if (pp.savingPercent > (out[pp.period] ?? 0)) out[pp.period] = pp.savingPercent;
      })
    );
    return out;
  });

  /** Plans whose feature list is expanded on phones. */
  readonly expanded = signal<ReadonlySet<number>>(new Set());
  readonly featurePreview = 5;

  toggleMenu(): void {
    this.menuOpen.update((v) => !v);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  readonly menuOpen = signal(false);

  readonly features: Feature[] = [
    { icon: 'fitness_center', tone: 'orange', size: 'wide', title: 'Rutinas y registro de entrenos con RIR', text: 'Tu rutina en el móvil: apunta series, kilos y RIR en cada sesión y tu entrenador ve exactamente cómo rindes.' },
    { icon: 'restaurant', tone: 'green', title: 'Dieta a medida', text: 'Plan de alimentación adaptado a tus objetivos y a tu día a día.' },
    { icon: 'event_repeat', tone: 'blue', title: 'Seguimiento y revisiones', text: 'Envía tus fotos, tu peso y un comentario; tu coach lo revisa y te da feedback.' },
    { icon: 'forum', tone: 'violet', title: 'Chat con tu entrenador', text: 'Resuelve dudas al momento, sin esperar a la próxima revisión.' },
    { icon: 'photo_camera', tone: 'orange', title: 'Fotos y progreso', text: 'Todas tus fotos de seguimiento en una galería para comparar tu evolución.' },
    { icon: 'credit_card', tone: 'green', size: 'wide', title: 'Pagos con tarjeta o Bizum', text: 'Paga cada mes, trimestre, semestre o año en segundos, sin letra pequeña.' },
  ];

  readonly steps: Step[] = [
    { number: '01', title: 'El entrenador crea tu plan', text: 'Diseñamos tu entrenamiento y dieta a partir de tus objetivos.' },
    { number: '02', title: 'Tú sigues dieta y rutina', text: 'Accede a todo desde tu área privada, estés donde estés.' },
    { number: '03', title: 'Envías tu seguimiento', text: 'Sube de 4 a 6 fotos, tu peso y un comentario de cómo te sientes.' },
    { number: '04', title: 'Recibes feedback personalizado', text: 'Tu coach revisa y ajusta tu plan para seguir progresando.' },
  ];

  readonly testimonials = signal<PublicTestimonial[]>([]);
  readonly starSlots = [1, 2, 3, 4, 5];

  readonly averageRating = computed(() => {
    const list = this.testimonials();
    if (list.length === 0) return null;
    return list.reduce((sum, t) => sum + t.rating, 0) / list.length;
  });

  ngOnInit(): void {
    this.planService.findAll(true).subscribe({
      next: (plans) => {
        this.apiPlans.set(plans);
        this.plansLoading.set(false);
      },
      error: () => this.plansLoading.set(false),
    });
    this.testimonialService.findPublic().subscribe({
      next: (list) => this.testimonials.set(list),
      error: () => this.testimonials.set([]),
    });
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toUpperCase();
  }

  /** Price of the selected period (null when this plan doesn't offer it). */
  priceFor(plan: Plan): PlanPeriodPrice | null {
    return (plan.prices ?? []).find((pp) => pp.period === this.period()) ?? null;
  }

  monthlyOption(plan: Plan): PlanPeriodPrice {
    return (
      (plan.prices ?? []).find((pp) => pp.period === 'MONTHLY') ?? {
        period: 'MONTHLY',
        months: 1,
        price: plan.monthlyPrice,
        monthlyEquivalent: plan.monthlyPrice,
        savingPercent: 0,
      }
    );
  }

  featureItems(plan: Plan): { title: string; detail: string }[] {
    return (plan.features ?? []).map(splitFeature);
  }

  reviewLabel(plan: Plan): string {
    const d = plan.reviewFrequencyDays;
    if (d === 7) return 'Revisión semanal';
    if (d === 14 || d === 15) return 'Revisión quincenal';
    if (d === 30) return 'Revisión mensual';
    return `Revisión cada ${d} días`;
  }

  isExpanded(plan: Plan): boolean {
    return this.expanded().has(plan.id);
  }

  toggleExpanded(plan: Plan): void {
    this.expanded.update((set) => {
      const next = new Set(set);
      if (next.has(plan.id)) next.delete(plan.id);
      else next.add(plan.id);
      return next;
    });
  }

  isFeatured(plan: Plan): boolean {
    return plan.id === this.featuredPlanId();
  }
}
