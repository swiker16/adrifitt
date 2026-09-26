import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { LeadService } from '../../core/services/lead.service';
import { PlanService } from '../../core/services/plan.service';
import { Plan } from '../../shared/models/plan.model';
import { apiErrorMessage } from '../../shared/utils/download';
import { PublicFrame } from './public-frame';

/** "Quiero empezar": contact request reviewed by the trainer before any account is created. */
@Component({
  selector: 'app-apply-page',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, PublicFrame],
  template: `
    <app-public-frame>
      @if (!sent()) {
        <div class="page-head">
          <h1>Empieza tu <span class="accent">cambio</span></h1>
          <p>Cuéntanos quién eres y qué quieres conseguir. Adri revisará tu solicitud personalmente.</p>
        </div>

        <ol class="timeline" aria-label="Cómo funciona">
          <li class="on"><span class="dot">1</span>Tu solicitud</li>
          <li><span class="dot">2</span>Cuestionario</li>
          <li><span class="dot">3</span>¡Bienvenida!</li>
        </ol>

        <form class="card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="form-grid">
            <div class="field">
              <label for="ap-first">Nombre</label>
              <input id="ap-first" formControlName="firstName" autocomplete="given-name" maxlength="100" />
              @if (invalid('firstName')) { <small class="err">Escribe tu nombre.</small> }
            </div>
            <div class="field">
              <label for="ap-last">Apellidos</label>
              <input id="ap-last" formControlName="lastName" autocomplete="family-name" maxlength="100" />
              @if (invalid('lastName')) { <small class="err">Escribe tus apellidos.</small> }
            </div>
            <div class="field">
              <label for="ap-email">Email</label>
              <input id="ap-email" type="email" formControlName="email" autocomplete="email" inputmode="email" maxlength="320" />
              @if (invalid('email')) { <small class="err">Escribe un email válido.</small> }
            </div>
            <div class="field">
              <label for="ap-phone">Teléfono <span class="opt">(opcional)</span></label>
              <input id="ap-phone" type="tel" formControlName="phone" autocomplete="tel" inputmode="tel" maxlength="30" />
            </div>
            <div class="field full">
              <label for="ap-plan">Plan que te interesa</label>
              <select id="ap-plan" formControlName="planId">
                <option [ngValue]="null">Aún no lo sé</option>
                @for (p of plans(); track p.id) { <option [ngValue]="p.id">{{ p.name }} · desde {{ p.monthlyPrice }} €/mes</option> }
              </select>
            </div>
            <div class="field full">
              <label for="ap-objective">¿Qué quieres conseguir?</label>
              <textarea id="ap-objective" formControlName="objective" rows="5" maxlength="1500"
                        placeholder="p. ej. Quiero perder 8 kg, tengo poco tiempo y me cuesta ser constante con la dieta…"></textarea>
              <span class="hint">{{ form.controls.objective.value.length }}/1500</span>
              @if (invalid('objective')) { <small class="err">Cuéntanos tu objetivo (mínimo 10 caracteres).</small> }
            </div>
          </div>

          <!-- Honeypot: invisible to people, bots fill it. -->
          <div class="hp" aria-hidden="true">
            <label for="ap-website">Web</label>
            <input id="ap-website" formControlName="website" tabindex="-1" autocomplete="off" />
          </div>

          <label class="check">
            <input type="checkbox" formControlName="consent" />
            <span>Acepto que AdriFitt use estos datos para responder a mi solicitud. No los compartimos con nadie.</span>
          </label>
          @if (invalid('consent')) { <small class="err">Necesitamos tu permiso para responderte.</small> }

          @if (error()) { <div class="alert error err-box"><mat-icon>error</mat-icon><span>{{ error() }}</span></div> }

          <button type="submit" class="btn btn-primary btn-lg btn-block" [disabled]="sending()">
            @if (sending()) { Enviando… } @else { Enviar solicitud <mat-icon>send</mat-icon> }
          </button>
          <p class="login-hint">¿Ya eres cliente? <a routerLink="/login">Entra en tu área</a></p>
        </form>
      } @else {
        <div class="card">
          <div class="state">
            <div class="big-icon"><mat-icon>mark_email_read</mat-icon></div>
            <h2>¡Solicitud enviada!</h2>
            <p>Gracias, {{ form.controls.firstName.value }}. Adri la revisará personalmente y te escribirá a
              <strong>{{ form.controls.email.value }}</strong>, normalmente en 24-48 horas.</p>
            <p>Si encaja, recibirás un cuestionario para conocerte mejor. Revisa también la carpeta de spam.</p>
            <a routerLink="/" class="btn btn-outline">Volver al inicio</a>
          </div>
        </div>
      }
    </app-public-frame>
  `,
  styleUrl: './public-page.scss',
})
export class ApplyPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leads = inject(LeadService);
  private readonly planService = inject(PlanService);
  private readonly route = inject(ActivatedRoute);

  readonly plans = signal<Plan[]>([]);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    planId: this.fb.control<number | null>(null),
    objective: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(1500)]],
    consent: [false, Validators.requiredTrue],
    website: [''],
  });

  ngOnInit(): void {
    const plan = Number(this.route.snapshot.queryParamMap.get('plan'));
    this.planService.findAll(true).subscribe({
      next: (list) => {
        this.plans.set(list);
        if (plan && list.some((p) => p.id === plan)) this.form.controls.planId.setValue(plan);
      },
      error: () => undefined,
    });
  }

  invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.leads.contact({ ...v, phone: v.phone || null }).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.set(true);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo enviar la solicitud. Inténtalo de nuevo.'));
      },
    });
  }
}
