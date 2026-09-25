import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { ClientService } from '../../../core/services/client.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { Client } from '../../../shared/models/client.model';
import { MeResponse } from '../../../shared/models/auth.model';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const a = group.get('newPassword')?.value;
  const b = group.get('repeatPassword')?.value;
  return a && b && a !== b ? { mismatch: true } : null;
}

@Component({
  selector: 'app-client-profile',
  imports: [DatePipe, ReactiveFormsModule, MatIconModule],
  templateUrl: './client-profile.html',
  styleUrl: './client-profile.scss',
})
export class ClientProfile {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly mustChange = this.auth.mustChangePassword;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly client = signal<Client | null>(null);
  readonly me = signal<MeResponse | null>(null);

  readonly saving = signal(false);
  readonly pwError = signal<string | null>(null);
  readonly showPw = signal(false);

  readonly fullName = computed(() => {
    const c = this.client();
    if (c) return `${c.firstName} ${c.lastName}`;
    const m = this.me();
    return m ? `${m.firstName ?? ''} ${m.lastName ?? ''}`.trim() || m.username : '';
  });
  readonly initials = computed(() =>
    this.fullName()
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((s) => s[0])
      .join('')
      .toUpperCase()
  );

  readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      repeatPassword: ['', Validators.required],
    },
    { validators: passwordsMatch }
  );

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    let pending = 2;
    const done = () => {
      if (--pending === 0) this.loading.set(false);
    };
    this.clientService.findMe().subscribe({
      next: (c) => {
        this.client.set(c);
        done();
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'No se pudieron cargar tus datos.'));
        done();
      },
    });
    this.auth.me().subscribe({
      next: (m) => {
        this.me.set(m);
        done();
      },
      error: () => done(),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) return;
    const v = this.form.getRawValue();
    if (v.newPassword === v.currentPassword) {
      this.pwError.set('La nueva contraseña debe ser distinta de la actual.');
      return;
    }
    const wasTemporary = this.mustChange();
    this.saving.set(true);
    this.pwError.set(null);
    this.auth.changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword }).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset();
        this.notify.success('Contraseña actualizada');
        if (wasTemporary) this.router.navigateByUrl('/client/dashboard');
      },
      error: (err) => {
        this.saving.set(false);
        this.pwError.set(
          apiErrorMessage(err, err?.status === 400 || err?.status === 401 ? 'La contraseña actual no es correcta.' : 'No se pudo cambiar la contraseña.')
        );
      },
    });
  }
}
