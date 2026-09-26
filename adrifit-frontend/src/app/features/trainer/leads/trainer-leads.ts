import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import { DashboardService } from '../../../core/services/dashboard.service';
import { LeadService } from '../../../core/services/lead.service';
import { NotifyService } from '../../../core/services/notify.service';
import { PlanService } from '../../../core/services/plan.service';
import { QuestionnaireView } from '../../../shared/components/questionnaire-view';
import { LEAD_STATUS_LABEL, LeadCounts, LeadDetail, LeadStatus, LeadSummary } from '../../../shared/models/lead.model';
import { BillingPeriod, Plan } from '../../../shared/models/plan.model';
import { apiErrorMessage } from '../../../shared/utils/download';
import { PlanPricingPicker } from '../clients/plan-pricing-picker';

type Tab = 'PENDING' | LeadStatus | 'ALL';
type Action = 'questionnaire' | 'reject' | 'approve';

/** "Solicitudes": new people who want to train with Adri, from the contact form to the account. */
@Component({
  selector: 'app-trainer-leads',
  imports: [DatePipe, FormsModule, RouterLink, MatIconModule, QuestionnaireView, PlanPricingPicker],
  templateUrl: './trainer-leads.html',
  styleUrl: './trainer-leads.scss',
})
export class TrainerLeads implements OnInit {
  private readonly leadService = inject(LeadService);
  private readonly planService = inject(PlanService);
  private readonly notify = inject(NotifyService);
  private readonly dashboard = inject(DashboardService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statusLabel = LEAD_STATUS_LABEL;
  readonly leads = signal<LeadSummary[]>([]);
  readonly counts = signal<LeadCounts | null>(null);
  readonly plans = signal<Plan[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly tab = signal<Tab>('PENDING');
  readonly selected = signal<LeadDetail | null>(null);
  readonly loadingDetail = signal(false);

  // Action dialog
  readonly action = signal<Action | null>(null);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  message = '';
  planId: number | null = null;
  period: BillingPeriod = 'MONTHLY';
  special = false;
  customPrice: number | null = null;
  customNote = '';
  note = '';

  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'PENDING', label: 'Pendientes' },
    { id: 'QUESTIONNAIRE_SENT', label: 'Esperando cuestionario' },
    { id: 'ACCEPTED', label: 'Aceptadas' },
    { id: 'REJECTED', label: 'Rechazadas' },
    { id: 'ALL', label: 'Todas' },
  ];

  readonly visible = computed(() => {
    const t = this.tab();
    const list = this.leads();
    if (t === 'ALL') return list;
    if (t === 'PENDING') {
      // Answered questionnaires first (ready to decide), then new requests.
      return list.filter((l) => l.status === 'NEW' || l.status === 'QUESTIONNAIRE_COMPLETED')
        .sort((a, b) => (a.status === b.status ? b.createdAt.localeCompare(a.createdAt) : a.status === 'QUESTIONNAIRE_COMPLETED' ? -1 : 1));
    }
    return list.filter((l) => l.status === t);
  });

  ngOnInit(): void {
    this.load();
    this.planService.findAll(true).subscribe({ next: (p) => this.plans.set(p), error: () => undefined });
    const id = Number(this.route.snapshot.queryParamMap.get('id'));
    if (id) this.open(id);
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.leadService.findAll().subscribe({
      next: (list) => {
        this.leads.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(apiErrorMessage(err, 'No se pudieron cargar las solicitudes.'));
      },
    });
    this.leadService.counts().subscribe({ next: (c) => this.counts.set(c), error: () => undefined });
  }

  countFor(t: Tab): number | null {
    const c = this.counts();
    if (!c) return null;
    switch (t) {
      case 'PENDING': return c.newRequests + c.toReview;
      case 'QUESTIONNAIRE_SENT': return c.questionnaireSent;
      case 'ACCEPTED': return c.accepted;
      case 'REJECTED': return c.rejected;
      default: return c.newRequests + c.toReview + c.questionnaireSent + c.accepted + c.rejected;
    }
  }

  badgeClass(s: LeadStatus): string {
    return { NEW: 'blue', QUESTIONNAIRE_SENT: 'gray', QUESTIONNAIRE_COMPLETED: 'orange', ACCEPTED: 'green', REJECTED: 'red' }[s];
  }

  initials(l: LeadSummary): string {
    return ((l.firstName[0] ?? '') + (l.lastName[0] ?? '')).toUpperCase();
  }

  whatsapp(phone: string | null): string | null {
    if (!phone) return null;
    const digits = phone.replace(/\D/g, '');
    return `https://wa.me/${digits.length === 9 ? '34' + digits : digits}`;
  }

  open(id: number): void {
    this.loadingDetail.set(true);
    this.router.navigate([], { queryParams: { id }, replaceUrl: true });
    this.leadService.findById(id).subscribe({
      next: (d) => {
        this.selected.set(d);
        this.note = d.trainerNote ?? '';
        this.loadingDetail.set(false);
      },
      error: (err) => {
        this.loadingDetail.set(false);
        this.notify.error(err, 'No se pudo abrir la solicitud.');
      },
    });
  }

  close(): void {
    this.selected.set(null);
    this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  startAction(a: Action): void {
    const d = this.selected();
    if (!d) return;
    this.message = '';
    this.submitted.set(false);
    if (a === 'approve') {
      const q = d.questionnaire;
      this.planId = q?.planId ?? d.lead.preferredPlanId ?? this.plans()[0]?.id ?? null;
      this.period = q?.billingPeriod ?? 'MONTHLY';
      this.special = false;
      this.customPrice = null;
      this.customNote = '';
    }
    this.action.set(a);
  }

  async confirmAction(): Promise<void> {
    const d = this.selected();
    const a = this.action();
    if (!d || !a) return;
    this.submitted.set(true);
    if (a === 'approve' && (!this.planId || (this.special && (this.customPrice === null || this.customPrice < 0)))) return;
    this.busy.set(true);
    try {
      const id = d.lead.id;
      let updated: LeadDetail;
      if (a === 'questionnaire') {
        updated = await firstValueFrom(this.leadService.sendQuestionnaire(id, this.message));
        this.notify.success(`Cuestionario enviado a ${d.lead.firstName}`);
      } else if (a === 'reject') {
        updated = await firstValueFrom(this.leadService.reject(id, this.message));
        this.notify.success(`Se ha enviado el email a ${d.lead.firstName}`);
      } else {
        updated = await firstValueFrom(this.leadService.approve(id, {
          planId: this.planId!,
          billingPeriod: this.period,
          customPrice: this.special ? this.customPrice : null,
          customPriceNote: this.special ? this.customNote || null : null,
          message: this.message || null,
        }));
        this.notify.success(`¡${d.lead.firstName} ya es cliente! Le hemos enviado el email para activar su cuenta.`);
      }
      this.selected.set(updated);
      this.action.set(null);
      this.load();
      this.dashboard.notifyChanged();
    } catch (err) {
      this.notify.error(err, 'No se pudo completar la acción.');
    } finally {
      this.busy.set(false);
    }
  }

  resendActivation(): void {
    const d = this.selected();
    if (!d) return;
    this.leadService.resendActivation(d.lead.id).subscribe({
      next: () => this.notify.success('Enlace de activación reenviado'),
      error: (err) => this.notify.error(err, 'No se pudo reenviar.'),
    });
  }

  saveNote(): void {
    const d = this.selected();
    if (!d || (d.trainerNote ?? '') === this.note) return;
    this.leadService.updateNote(d.lead.id, this.note || null).subscribe({
      next: (u) => {
        this.selected.set(u);
        this.notify.success('Nota guardada');
      },
      error: (err) => this.notify.error(err, 'No se pudo guardar la nota.'),
    });
  }

  remove(): void {
    const d = this.selected();
    if (!d) return;
    if (!confirm(`¿Eliminar la solicitud de ${d.lead.firstName} ${d.lead.lastName}? Se borrarán también sus respuestas de salud.`)) return;
    this.leadService.delete(d.lead.id).subscribe({
      next: () => {
        this.notify.success('Solicitud eliminada');
        this.close();
        this.load();
        this.dashboard.notifyChanged();
      },
      error: (err) => this.notify.error(err, 'No se pudo eliminar.'),
    });
  }
}
