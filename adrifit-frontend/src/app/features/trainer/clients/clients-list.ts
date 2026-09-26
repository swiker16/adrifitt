import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ClientService } from '../../../core/services/client.service';
import { PlanService } from '../../../core/services/plan.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { Client, CreateClientRequest } from '../../../shared/models/client.model';
import { BillingPeriod, Plan } from '../../../shared/models/plan.model';
import { PlanPricingPicker } from './plan-pricing-picker';

type CopyKind = 'username' | 'password' | 'both';

@Component({
  selector: 'app-clients-list',
  imports: [RouterLink, DatePipe, ReactiveFormsModule, MatIconModule, PlanPricingPicker],
  templateUrl: './clients-list.html',
  styleUrl: './clients-list.scss',
})
export class ClientsList {
  private readonly clientService = inject(ClientService);
  private readonly planService = inject(PlanService);
  private readonly notify = inject(NotifyService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly clients = signal<Client[]>([]);
  readonly plans = signal<Plan[]>([]);
  readonly plansError = signal<string | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly showForm = signal(false);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly createdInfo = signal<{ name: string; email: string; username: string; password: string } | null>(null);
  readonly copied = signal<CopyKind | null>(null);

  // Billing period + special conditions of the new client's subscription.
  readonly period = signal<BillingPeriod>('MONTHLY');
  readonly special = signal(false);
  readonly customPrice = signal<number | null>(null);
  readonly customNote = signal('');
  readonly submitted = signal(false);

  readonly search = signal('');
  readonly sort = signal<'recent' | 'name'>('recent');

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    let list = this.clients();
    if (q) {
      list = list.filter((c) =>
        [c.firstName, c.lastName, c.email, c.phone, c.objective]
          .some((v) => (v ?? '').toLowerCase().includes(q))
        || `${c.firstName} ${c.lastName}`.toLowerCase().includes(q));
    }
    if (this.sort() === 'name') {
      list = [...list].sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`, 'es'));
    }
    return list;
  });

  readonly form = this.fb.group({
    firstName:  this.fb.nonNullable.control('', Validators.required),
    lastName:   this.fb.nonNullable.control('', Validators.required),
    phone:      this.fb.nonNullable.control('', Validators.required),
    birthDate:  this.fb.nonNullable.control('', Validators.required),
    objective:  this.fb.nonNullable.control('', Validators.required),
    email:      this.fb.nonNullable.control('', [Validators.required, Validators.email]),
    notes:      this.fb.nonNullable.control(''),
    planId:     this.fb.control<number | null>(null, Validators.required),
  });

  constructor() {
    this.load();
    this.planService.findAll(true).subscribe({
      next: (p) => this.plans.set([...p].sort((a, b) => a.monthlyPrice - b.monthlyPrice)),
      error: (err) => this.plansError.set(apiErrorMessage(err, 'No se pudieron cargar los planes.')),
    });
    if (this.route.snapshot.queryParamMap.has('new')) {
      this.showForm.set(true);
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.clientService.findAll().subscribe({
      next: (data) => {
        this.clients.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  toggleForm(): void {
    this.showForm.update((v) => !v);
    this.formError.set(null);
    if (!this.showForm()) this.resetForm();
  }

  private resetForm(): void {
    this.form.reset();
    this.period.set('MONTHLY');
    this.special.set(false);
    this.customPrice.set(null);
    this.customNote.set('');
    this.submitted.set(false);
  }

  private specialInvalid(): boolean {
    if (!this.special()) return false;
    const c = this.customPrice();
    return c === null || !Number.isFinite(c) || c < 0;
  }

  initials(c: Client): string {
    return (c.firstName?.[0] ?? '') + (c.lastName?.[0] ?? '');
  }

  save(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.specialInvalid()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) return;

    this.saving.set(true);
    this.formError.set(null);

    const raw = this.form.getRawValue();
    // trainerId is optional: the backend assigns the logged-in trainer.
    const payload: CreateClientRequest = {
      firstName: raw.firstName.trim(),
      lastName:  raw.lastName.trim(),
      phone:     raw.phone.trim(),
      birthDate: raw.birthDate,
      objective: raw.objective.trim(),
      email:     raw.email.trim(),
      planId:    raw.planId!,
      billingPeriod: this.period(),
      customPrice: this.special() ? this.customPrice() : null,
      customPriceNote: this.special() ? this.customNote().trim() || null : null,
      notes:     raw.notes?.trim() || undefined,
    };

    this.clientService.create(payload).subscribe({
      next: (res) => {
        this.clients.update((list) => [res.client, ...list]);
        this.createdInfo.set({
          name: `${res.client.firstName} ${res.client.lastName}`,
          email: res.client.email ?? payload.email,
          username: res.username,
          password: res.temporaryPassword,
        });
        this.saving.set(false);
        this.resetForm();
        this.showForm.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(
          apiErrorMessage(err, err?.status === 409 ? 'Ya existe un usuario con ese email.' : 'No se pudo crear el cliente.')
        );
      },
    });
  }

  async copy(kind: CopyKind): Promise<void> {
    const info = this.createdInfo();
    if (!info) return;
    const text =
      kind === 'username' ? info.username
      : kind === 'password' ? info.password
      : `Usuario: ${info.username}\nContraseña temporal: ${info.password}`;
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(kind);
      setTimeout(() => this.copied.set(null), 2000);
    } catch {
      this.notify.error('No se pudo copiar. Selecciona el texto y cópialo manualmente.');
    }
  }

  closeCreated(): void {
    this.createdInfo.set(null);
    this.copied.set(null);
  }
}
