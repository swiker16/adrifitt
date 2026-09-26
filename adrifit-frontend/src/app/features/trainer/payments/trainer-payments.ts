import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PaymentService } from '../../../core/services/payment.service';
import { ClientService } from '../../../core/services/client.service';
import { TaskService } from '../../../core/services/task.service';
import { NotifyService } from '../../../core/services/notify.service';
import { Client } from '../../../shared/models/client.model';
import {
  Payment,
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS_LABEL,
  PaymentStatus,
  PaymentSummary,
} from '../../../shared/models/payment.model';
import { isoDate } from '../../../shared/utils/download';

type StatusFilter = PaymentStatus | 'ALL';

@Component({
  selector: 'app-trainer-payments',
  imports: [MatIconModule, FormsModule, DatePipe, DecimalPipe, RouterLink, NgTemplateOutlet],
  templateUrl: './trainer-payments.html',
  styleUrl: './trainer-payments.scss',
})
export class TrainerPayments {
  private readonly paymentService = inject(PaymentService);
  private readonly clientService = inject(ClientService);
  private readonly taskService = inject(TaskService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);

  readonly statusLabel = PAYMENT_STATUS_LABEL;
  readonly methodLabel = PAYMENT_METHOD_LABEL;
  readonly statusOptions: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'PENDING', label: 'Pendientes' },
    { value: 'PAID', label: 'Pagados' },
    { value: 'REFUNDED', label: 'Devueltos' },
    { value: 'CANCELLED', label: 'Anulados' },
  ];

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly payments = signal<Payment[]>([]);
  readonly summary = signal<PaymentSummary | null>(null);
  readonly clients = signal<Client[]>([]);

  readonly statusFilter = signal<StatusFilter>('ALL');
  readonly clientFilter = signal<number | null>(null);

  readonly busyId = signal<number | null>(null);
  readonly runningJobs = signal(false);

  // Cash modal
  readonly cashTarget = signal<Payment | null>(null);
  readonly cashNotes = signal('');

  // New payment modal
  readonly showCreate = signal(false);
  readonly creating = signal(false);
  readonly createError = signal<string | null>(null);
  newClientId: number | null = null;
  newAmount: number | null = null;
  newConcept = '';
  newDueDate = isoDate();

  readonly sortedClients = computed(() =>
    [...this.clients()].sort((a, b) => this.fullName(a).localeCompare(this.fullName(b), 'es')),
  );

  constructor() {
    const qp = Number(this.route.snapshot.queryParamMap.get('clientId'));
    if (qp > 0) this.clientFilter.set(qp);
    this.clientService.findAll().subscribe({
      next: (c) => this.clients.set(c),
      error: () => this.clients.set([]),
    });
    this.loadSummary();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    const status = this.statusFilter();
    this.paymentService
      .findAll({ status: status === 'ALL' ? null : status, clientId: this.clientFilter() })
      .subscribe({
        next: (p) => {
          this.payments.set(p);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  loadSummary(): void {
    this.paymentService.summary().subscribe({
      next: (s) => this.summary.set(s),
      error: () => this.summary.set(null),
    });
  }

  setStatus(s: StatusFilter): void {
    this.statusFilter.set(s);
    this.load();
  }

  setClient(value: number | string | null): void {
    const id = Number(value);
    this.clientFilter.set(id > 0 ? id : null);
    this.load();
  }

  fullName(c: Client): string {
    return `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim();
  }

  initials(name: string | null): string {
    return (name ?? '?')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0])
      .join('')
      .toUpperCase();
  }

  statusClass(s: PaymentStatus): string {
    return { PENDING: 'orange', PAID: 'green', REFUNDED: 'violet', CANCELLED: 'gray' }[s];
  }

  methodDetail(p: Payment): string {
    if (p.method === 'CARD' && p.cardLast4) return `${p.cardBrand ?? 'Tarjeta'} ****${p.cardLast4}`;
    if (p.method === 'BIZUM' && p.bizumPhone) return p.bizumPhone;
    return '';
  }

  private replace(updated: Payment): void {
    const status = this.statusFilter();
    this.payments.update((list) =>
      status !== 'ALL' && updated.status !== status
        ? list.filter((p) => p.id !== updated.id)
        : list.map((p) => (p.id === updated.id ? updated : p)),
    );
    this.loadSummary();
  }

  // ── Cash ──────────────────────────────────────────────────────────────
  openCash(p: Payment): void {
    this.cashNotes.set('');
    this.cashTarget.set(p);
  }

  confirmCash(): void {
    const p = this.cashTarget();
    if (!p) return;
    this.busyId.set(p.id);
    this.paymentService.markCashPaid(p.id, this.cashNotes().trim()).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.busyId.set(null);
        this.cashTarget.set(null);
        this.notify.success('Cobro en efectivo registrado');
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo registrar el cobro.');
      },
    });
  }

  cancel(p: Payment): void {
    if (!confirm(`¿Anular el cobro "${p.concept}" de ${p.clientName ?? 'este cliente'}?`)) return;
    this.busyId.set(p.id);
    this.paymentService.cancel(p.id).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.busyId.set(null);
        this.notify.success('Cobro anulado');
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo anular el cobro.');
      },
    });
  }

  refund(p: Payment): void {
    if (!confirm(`¿Devolver ${p.amount.toFixed(2)} € a ${p.clientName ?? 'este cliente'}?`)) return;
    this.busyId.set(p.id);
    this.paymentService.refund(p.id).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.busyId.set(null);
        this.notify.success('Pago devuelto');
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo devolver el pago.');
      },
    });
  }

  // ── Create ────────────────────────────────────────────────────────────
  openCreate(): void {
    this.newClientId = this.clientFilter();
    this.newAmount = null;
    this.newConcept = '';
    this.newDueDate = isoDate();
    this.createError.set(null);
    this.showCreate.set(true);
  }

  create(): void {
    const clientId = Number(this.newClientId);
    const amount = Number(this.newAmount);
    const concept = this.newConcept.trim();
    if (!clientId) return this.createError.set('Selecciona un cliente.');
    if (!amount || amount <= 0) return this.createError.set('El importe debe ser mayor que 0.');
    if (!concept) return this.createError.set('Indica el concepto del cobro.');
    this.createError.set(null);
    this.creating.set(true);
    this.paymentService
      .create({ clientId, amount, concept, dueDate: this.newDueDate || undefined })
      .subscribe({
        next: () => {
          this.creating.set(false);
          this.showCreate.set(false);
          this.notify.success('Cobro creado');
          this.load();
          this.loadSummary();
        },
        error: (err) => {
          this.creating.set(false);
          this.notify.error(err, 'No se pudo crear el cobro.');
        },
      });
  }

  // ── Daily jobs ────────────────────────────────────────────────────────
  runJobs(): void {
    this.runningJobs.set(true);
    this.taskService.runDailyJobs().subscribe({
      next: (r) => {
        this.runningJobs.set(false);
        this.notify.success(
          `Renovaciones procesadas: ${r.subscriptionsProcessed} · Revisiones creadas: ${r.reviewTasksCreated}`,
        );
        this.load();
        this.loadSummary();
      },
      error: (err) => {
        this.runningJobs.set(false);
        this.notify.error(err, 'No se pudieron ejecutar las renovaciones.');
      },
    });
  }
}
