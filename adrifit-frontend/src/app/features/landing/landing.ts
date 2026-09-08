import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PlanService } from '../../core/services/plan.service';
import { Plan } from '../../shared/models/plan.model';

interface Benefit {
  icon: string;
  title: string;
  text: string;
}

interface Step {
  number: string;
  title: string;
  text: string;
}

interface Testimonial {
  name: string;
  role: string;
  quote: string;
  initials: string;
}

@Component({
  selector: 'app-landing',
  imports: [RouterLink, MatIconModule, DecimalPipe],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private readonly planService = inject(PlanService);

  readonly apiPlans = signal<Plan[]>([]);
  readonly plansLoading = signal(true);

  readonly featuredPlanId = computed(() => {
    const plans = this.apiPlans();
    if (plans.length === 0) return null;
    const mid = Math.floor(plans.length / 2);
    return plans[mid]?.id ?? null;
  });

  readonly benefits: Benefit[] = [
    { icon: 'fitness_center', title: 'Entrenamiento a medida', text: 'Rutinas diseñadas para tu nivel, objetivos y disponibilidad.' },
    { icon: 'restaurant', title: 'Nutrición personalizada', text: 'Planes de alimentación adaptados a tu día a día.' },
    { icon: 'event_repeat', title: 'Seguimiento semanal', text: 'Revisión constante de tu progreso semana a semana.' },
    { icon: 'trending_up', title: 'Evolución con reportes', text: 'Visualiza tu evolución con datos reales y feedback.' },
  ];

  readonly steps: Step[] = [
    { number: '01', title: 'El entrenador crea tu plan', text: 'Diseñamos tu entrenamiento y dieta a partir de tus objetivos.' },
    { number: '02', title: 'Tú sigues dieta y rutina', text: 'Accede a todo desde tu área privada, estés donde estés.' },
    { number: '03', title: 'Envías tu reporte semanal', text: 'Registra peso, medidas y sensaciones cada semana.' },
    { number: '04', title: 'Recibes feedback personalizado', text: 'Tu coach revisa y ajusta tu plan para seguir progresando.' },
  ];

  readonly testimonials: Testimonial[] = [
    { name: 'Laura M.', role: 'Cliente Pro · 8 meses', initials: 'LM', quote: 'En 6 meses logré los resultados que llevaba años buscando. El seguimiento semanal marca la diferencia.' },
    { name: 'Carlos R.', role: 'Cliente Elite · 1 año', initials: 'CR', quote: 'Tener un plan a medida y feedback real cada semana me mantiene constante y motivado.' },
    { name: 'Marta G.', role: 'Cliente Pro · 4 meses', initials: 'MG', quote: 'La plataforma es clarísima. Subo mi reporte y en horas tengo los ajustes de mi coach.' },
  ];

  ngOnInit(): void {
    this.planService.findAll(true).subscribe({
      next: (plans) => {
        this.apiPlans.set(plans);
        this.plansLoading.set(false);
      },
      error: () => this.plansLoading.set(false),
    });
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
