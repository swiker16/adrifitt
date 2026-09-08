import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { ClientService } from '../../../core/services/client.service';
import { PlanService } from '../../../core/services/plan.service';
import { Client } from '../../../shared/models/client.model';
import { Plan } from '../../../shared/models/plan.model';

@Component({
  selector: 'app-clients-list',
  imports: [RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, MatIconModule],
  templateUrl: './clients-list.html',
  styleUrl: './clients-list.scss',
})
export class ClientsList {
  private readonly clientService = inject(ClientService);
  private readonly planService = inject(PlanService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly clients = signal<Client[]>([]);
  readonly plans = signal<Plan[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly showForm = signal(false);
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly createdInfo = signal<{ name: string; username: string; password: string } | null>(null);

  readonly filtered = computed(() => this.clients());

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
    this.planService.findAll(true).subscribe({ next: (p) => this.plans.set(p), error: () => {} });
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
    if (!this.showForm()) this.form.reset();
  }

  initials(c: Client): string {
    return (c.firstName?.[0] ?? '') + (c.lastName?.[0] ?? '');
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const trainerId = this.auth.user()?.userId;
    if (!trainerId) return;

    this.saving.set(true);
    this.formError.set(null);

    const raw = this.form.getRawValue();
    const payload = {
      firstName: raw.firstName,
      lastName:  raw.lastName,
      phone:     raw.phone,
      birthDate: raw.birthDate,
      objective: raw.objective,
      email:     raw.email,
      planId:    raw.planId!,
      trainerId,
      notes:     raw.notes || undefined,
    };

    this.clientService.create(payload).subscribe({
      next: (res) => {
        this.clients.update((list) => [res.client, ...list]);
        this.createdInfo.set({
          name: `${res.client.firstName} ${res.client.lastName}`,
          username: res.username,
          password: res.temporaryPassword,
        });
        this.saving.set(false);
        this.form.reset();
        this.showForm.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(
          err?.status === 409
            ? 'El email ya está en uso.'
            : 'No se pudo crear el cliente.'
        );
      },
    });
  }
}
