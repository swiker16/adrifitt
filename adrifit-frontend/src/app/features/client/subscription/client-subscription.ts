import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { PlanService } from '../../../core/services/plan.service';
import { PaymentService } from '../../../core/services/payment.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import {
  BILLING_PERIOD_LABEL,
  BILLING_PERIOD_SUFFIX,
  BILLING_PERIODS,
  BillingPeriod,
  Plan,
  PlanPeriodPrice,
  splitFeature,
} from '../../../shared/models/plan.model';
import { Subscription, SUBSCRIPTION_STATUS_LABEL } from '../../../shared/models/subscription.model';
import {
  Payment,
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS_LABEL,
  PaymentMethod,
  PaymentStatus,
} from '../../../shared/models/payment.model';

type LoadState = 'loading' | 'ok' | 'none' | 'error';
type PayTab = 'card' | 'bizum';

/** Card number: 13–19 digits (spaces ignored). */
function cardNumberValidator(c: AbstractControl): ValidationErrors | null {
  const digits = String(c.value ?? '').replace(/\D/g, '');
  return digits.length >= 13 && digits.length <= 19 ? null : { cardNumber: true };
}

/** MM/AA, valid month and not expired. */
function expiryValidator(c: AbstractControl): ValidationErrors | null {
  const m = /^(\d{2})\/(\d{2})$/.exec(String(c.value ?? ''));
  if (!m) return { expiry: true };
  const month = Number(m[1]);
  const year = 2000 + Number(m[2]);
  if (month < 1 || month > 12) return { expiry: true };
  const now = new Date();
  if (year < now.getFullYear() || (year === now.getFullYear() && month < now.getMonth() + 1)) {
    return { expired: true };
  }
  return null;
}

/** Spanish phone: 9 digits (optional +34 prefix). */
function phoneValidator(c: AbstractControl): ValidationErrors | null {
  const digits = String(c.value ?? '').replace(/\D/g, '').replace(/^34(?=\d{9}$)/, '');
  return /^\d{9}$/.test(digits) ? null : { phone: true };
}

@Component({
  selector: 'app-client-subscription',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatIconModule],
  templateUrl: './client-subscription.html',
  styleUrl: './client-subscription.scss',
})
export class ClientSubscription {
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly planService = inject(PlanService);
  private readonly paymentService = inject(PaymentService);
  private readonly notify = inject(NotifyService);
  private readonly fb = inject(FormBuilder);

  readonly statusLabel = SUBSCRIPTION_STATUS_LABEL;
  readonly paymentStatusLabel = PAYMENT_STATUS_LABEL;
  readonly methodLabel = PAYMENT_METHOD_LABEL;
  readonly periodLabel = BILLING_PERIOD_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;

  // ── Subscription ─────────────────────────────────────────────
  readonly subState = signal<LoadState>('loading');
  readonly sub = signal<Subscription | null>(null);
  readonly subError = signal<string | null>(null);
  readonly history = signal<Subscription[]>([]);
  readonly historyLoaded = signal(false);

  // ── Plans ────────────────────────────────────────────────────
  readonly plans = signal<Plan[]>([]);
  readonly plansLoading = signal(true);
  /** Billing period shown in the plan cards (defaults to the current one). */
  readonly period = signal<BillingPeriod>('MONTHLY');
  readonly periods = computed<BillingPeriod[]>(() => {
    const offered = new Set<BillingPeriod>(['MONTHLY']);
    this.plans().forEach((p) => (p.prices ?? []).forEach((pp) => offered.add(pp.period)));
    return BILLING_PERIODS.filter((b) => offered.has(b));
  });

  // ── Payments ─────────────────────────────────────────────────
  readonly payments = signal<Payment[]>([]);
  readonly paymentsLoading = signal(true);
  readonly paymentsError = signal<string | null>(null);

  readonly pending = computed(() =>
    this.payments()
      .filter((p) => p.status === 'PENDING')
      .sort((a, b) => Number(b.overdue) - Number(a.overdue) || a.dueDate.localeCompare(b.dueDate))
  );
  readonly done = computed(() =>
    this.payments()
      .filter((p) => p.status !== 'PENDING')
      .sort((a, b) => (b.paidAt ?? b.createdAt).localeCompare(a.paidAt ?? a.createdAt))
  );

  readonly canCancel = computed(() => {
    const s = this.sub();
    return !!s && s.status === 'ACTIVE' && !s.cancelAtPeriodEnd;
  });

  // ── Modals ───────────────────────────────────────────────────
  readonly changeTarget = signal<Plan | null>(null);
  readonly changePeriod = signal<BillingPeriod>('MONTHLY');
  readonly changing = signal(false);
  readonly showCancel = signal(false);
  readonly cancelling = signal(false);
  readonly resuming = signal(false);

  readonly payTarget = signal<Payment | null>(null);
  readonly payTab = signal<PayTab>('card');
  readonly paying = signal(false);
  readonly payError = signal<string | null>(null);

  readonly cardForm = this.fb.nonNullable.group({
    holderName: ['', [Validators.required, Validators.maxLength(100)]],
    cardNumber: ['', [Validators.required, cardNumberValidator]],
    expiry: ['', [Validators.required, expiryValidator]],
    cvc: ['', [Validators.required, Validators.pattern(/^\d{3,4}$/)]],
  });

  readonly bizumForm = this.fb.nonNullable.group({
    phone: ['', [Validators.required, phoneValidator]],
  });

  constructor() {
    this.loadSubscription();
    this.loadHistory();
    this.loadPayments();
    this.planService.findAll(true).subscribe({
      next: (p) => {
        this.plans.set([...p].sort((a, b) => a.monthlyPrice - b.monthlyPrice));
        this.plansLoading.set(false);
      },
      error: () => this.plansLoading.set(false),
    });
  }

  // ── Loaders ──────────────────────────────────────────────────
  loadSubscription(): void {
    this.subState.set('loading');
    this.subscriptionService.getMine().subscribe({
      next: (s) => {
        this.sub.set(s);
        this.subState.set('ok');
        this.period.set(s.billingPeriod ?? 'MONTHLY');
      },
      error: (err) => {
        this.sub.set(null);
        if (err?.status === 404) {
          this.subState.set('none');
        } else {
          this.subError.set(apiErrorMessage(err, 'No se pudo cargar tu suscripción.'));
          this.subState.set('error');
        }
      },
    });
  }

  loadHistory(): void {
    this.subscriptionService.getMyHistory().subscribe({
      next: (h) => {
        this.history.set(h);
        this.historyLoaded.set(true);
      },
      error: () => this.historyLoaded.set(true),
    });
  }

  loadPayments(): void {
    this.paymentsError.set(null);
    this.paymentService.findMine().subscribe({
      next: (p) => {
        this.payments.set(p);
        this.paymentsLoading.set(false);
      },
      error: (err) => {
        this.paymentsError.set(apiErrorMessage(err, 'No se pudieron cargar tus pagos.'));
        this.paymentsLoading.set(false);
      },
    });
  }

  // ── Plan helpers ─────────────────────────────────────────────
  /** Same plan AND same billing period as the current subscription. */
  isCurrent(plan: Plan, period: BillingPeriod = this.period()): boolean {
    const s = this.sub();
    return !!s && s.planId === plan.id && s.billingPeriod === period;
  }

  isCurrentPlan(plan: Plan): boolean {
    const s = this.sub();
    return !!s && s.planId === plan.id;
  }

  priceFor(plan: Plan, period: BillingPeriod = this.period()): PlanPeriodPrice | null {
    return (plan.prices ?? []).find((pp) => pp.period === period) ?? null;
  }

  featureItems(plan: Plan | null | undefined): { title: string; detail: string }[] {
    return (plan?.features ?? []).map(splitFeature);
  }

  /** Plan price of the current subscription's period (before special conditions). */
  planPriceOf(s: Subscription): number | null {
    return this.priceFor(s.plan, s.billingPeriod)?.price ?? (s.billingPeriod === 'MONTHLY' ? s.plan.monthlyPrice : null);
  }

  statusBadge(s: Subscription): string {
    if (s.cancelAtPeriodEnd) return 'orange';
    return s.status === 'ACTIVE' ? 'green' : s.status === 'PAUSED' ? 'blue' : 'gray';
  }

  paymentBadge(p: Payment): string {
    const map: Record<PaymentStatus, string> = { PENDING: 'orange', PAID: 'green', REFUNDED: 'violet', CANCELLED: 'gray' };
    return p.overdue && p.status === 'PENDING' ? 'red' : map[p.status];
  }

  methodIcon(m: PaymentMethod | null): string {
    return m === 'CARD' ? 'credit_card' : m === 'BIZUM' ? 'smartphone' : m === 'CASH' ? 'payments' : 'receipt';
  }

  // ── Change plan ──────────────────────────────────────────────
  askChange(plan: Plan): void {
    if (this.isCurrent(plan)) return;
    const period = this.priceFor(plan) ? this.period() : 'MONTHLY';
    this.changePeriod.set(period);
    this.changeTarget.set(plan);
  }

  confirmChange(): void {
    const plan = this.changeTarget();
    if (!plan || this.changing() || this.isCurrent(plan, this.changePeriod())) return;
    this.changing.set(true);
    this.subscriptionService.changeMyPlan(plan.id, this.changePeriod()).subscribe({
      next: (s) => {
        this.sub.set(s);
        this.subState.set('ok');
        this.changing.set(false);
        this.changeTarget.set(null);
        this.period.set(s.billingPeriod ?? this.changePeriod());
        this.notify.success(`Ahora estás en el plan ${s.planName} (${BILLING_PERIOD_LABEL[s.billingPeriod ?? this.changePeriod()].toLowerCase()})`);
        this.loadPayments();
        this.loadHistory();
      },
      error: (err) => {
        this.changing.set(false);
        this.notify.error(err, 'No se pudo cambiar de plan.');
      },
    });
  }

  // ── Cancel / resume ──────────────────────────────────────────
  confirmCancel(): void {
    if (this.cancelling()) return;
    this.cancelling.set(true);
    this.subscriptionService.cancelMine().subscribe({
      next: (s) => {
        this.sub.set(s);
        this.cancelling.set(false);
        this.showCancel.set(false);
        this.notify.success('Suscripción cancelada. No habrá más cobros.');
        this.loadHistory();
      },
      error: (err) => {
        this.cancelling.set(false);
        this.notify.error(err, 'No se pudo cancelar la suscripción.');
      },
    });
  }

  resume(): void {
    if (this.resuming()) return;
    this.resuming.set(true);
    this.subscriptionService.resumeMine().subscribe({
      next: (s) => {
        this.sub.set(s);
        this.resuming.set(false);
        this.notify.success('¡Suscripción reactivada!');
        this.loadHistory();
      },
      error: (err) => {
        this.resuming.set(false);
        this.notify.error(err, 'No se pudo reactivar la suscripción.');
      },
    });
  }

  // ── Payments ─────────────────────────────────────────────────
  openPay(p: Payment): void {
    this.cardForm.reset();
    this.bizumForm.reset();
    this.payError.set(null);
    this.payTab.set('card');
    this.payTarget.set(p);
  }

  closePay(): void {
    if (this.paying()) return;
    this.payTarget.set(null);
    // Never keep card data around.
    this.cardForm.reset();
    this.bizumForm.reset();
  }

  setTab(tab: PayTab): void {
    this.payTab.set(tab);
    this.payError.set(null);
  }

  onCardNumberInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 19);
    const formatted = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
    input.value = formatted;
    this.cardForm.controls.cardNumber.setValue(formatted, { emitEvent: false });
  }

  onExpiryInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 4);
    const formatted = digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
    input.value = formatted;
    this.cardForm.controls.expiry.setValue(formatted, { emitEvent: false });
  }

  onCvcInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 4);
    input.value = digits;
    this.cardForm.controls.cvc.setValue(digits, { emitEvent: false });
  }

  submitPay(): void {
    const target = this.payTarget();
    if (!target || this.paying()) return;
    this.payError.set(null);

    let request$;
    if (this.payTab() === 'card') {
      if (this.cardForm.invalid) {
        this.cardForm.markAllAsTouched();
        return;
      }
      const v = this.cardForm.getRawValue();
      const [mm, yy] = v.expiry.split('/');
      request$ = this.paymentService.payWithCard(target.id, {
        holderName: v.holderName.trim(),
        cardNumber: v.cardNumber.replace(/\D/g, ''),
        expMonth: Number(mm),
        expYear: 2000 + Number(yy),
        cvc: v.cvc,
      });
    } else {
      if (this.bizumForm.invalid) {
        this.bizumForm.markAllAsTouched();
        return;
      }
      const phone = this.bizumForm.getRawValue().phone.replace(/[^\d+]/g, '');
      request$ = this.paymentService.payWithBizum(target.id, { phone });
    }

    this.paying.set(true);
    request$.subscribe({
      next: (paid) => {
        this.paying.set(false);
        this.payments.update((list) => list.map((p) => (p.id === paid.id ? paid : p)));
        this.payTarget.set(null);
        this.cardForm.reset();
        this.bizumForm.reset();
        this.notify.success('Pago realizado');
      },
      error: (err) => {
        this.paying.set(false);
        this.payError.set(
          apiErrorMessage(err, err?.status === 402 ? 'El pago ha sido rechazado.' : 'No se pudo procesar el pago.')
        );
        // Refresh failed attempts / reasons
        this.paymentService.findMine().subscribe({ next: (p) => this.payments.set(p), error: () => {} });
      },
    });
  }
}
