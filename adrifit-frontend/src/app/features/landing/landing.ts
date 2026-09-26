import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../core/services/plan.service';
import { Plan } from '../../shared/models/plan.model';
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
  imports: [RouterLink, MatIconModule, DecimalPipe],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private readonly planService = inject(PlanService);
  private readonly testimonialService = inject(TestimonialService);

  readonly apiPlans = signal<Plan[]>([]);
  readonly plansLoading = signal(true);

  readonly featuredPlanId = computed(() => {
    const plans = this.apiPlans();
    if (plans.length === 0) return null;
    const mid = Math.floor(plans.length / 2);
    return plans[mid]?.id ?? null;
  });

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
    { icon: 'event_repeat', tone: 'blue', title: 'Seguimiento y revisiones', text: 'Check-ins periódicos de peso, medidas y sensaciones con feedback.' },
    { icon: 'forum', tone: 'violet', title: 'Chat con tu entrenador', text: 'Resuelve dudas al momento, sin esperar a la próxima revisión.' },
    { icon: 'photo_camera', tone: 'orange', title: 'Fotos y progreso', text: 'Compara fotos y gráficas de evolución semana a semana.' },
    { icon: 'credit_card', tone: 'green', size: 'wide', title: 'Pagos con tarjeta o Bizum', text: 'Paga tu cuota mensual en segundos, sin permanencia ni letra pequeña.' },
  ];

  readonly steps: Step[] = [
    { number: '01', title: 'El entrenador crea tu plan', text: 'Diseñamos tu entrenamiento y dieta a partir de tus objetivos.' },
    { number: '02', title: 'Tú sigues dieta y rutina', text: 'Accede a todo desde tu área privada, estés donde estés.' },
    { number: '03', title: 'Envías tu reporte semanal', text: 'Registra peso, medidas y sensaciones cada semana.' },
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

  planFeatures(plan: Plan): string[] {
    const features: string[] = [];
    features.push(`Revisión cada ${plan.reviewFrequencyDays} días`);
    if (plan.messagingEnabled) features.push('Chat con tu coach');
    if (plan.analyticsEnabled) features.push('Analíticas avanzadas');
    if (plan.pdfExportEnabled) features.push('Exportación PDF de rutinas');
    if (plan.prioritySupport) features.push('Soporte prioritario');
    if (plan.description) features.push(plan.description);
    return features;
  }

  isFeatured(plan: Plan): boolean {
    return plan.id === this.featuredPlanId();
  }
}
