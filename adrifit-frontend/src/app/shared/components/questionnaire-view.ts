import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Q_LABELS, Questionnaire } from '../models/lead.model';
import { BILLING_PERIOD_LABEL } from '../models/plan.model';

/** Read-only view of the initial questionnaire (lead review and client file). */
@Component({
  selector: 'app-questionnaire-view',
  imports: [MatIconModule, DatePipe, DecimalPipe],
  template: `
    @if (q(); as q) {
      @if (q.hasInjuries || q.hasMedicalConditions) {
        <div class="alert warn health">
          <mat-icon>warning</mat-icon>
          <div>
            <strong>Atención a la salud</strong>
            @if (q.hasInjuries) { <p><b>Lesiones:</b> {{ q.injuries }}</p> }
            @if (q.hasMedicalConditions) { <p><b>Condiciones médicas:</b> {{ q.medicalConditions }}</p> }
          </div>
        </div>
      }

      <div class="sections">
        <section>
          <h4><mat-icon>person</mat-icon> Sobre ti</h4>
          <dl>
            <dt>Edad</dt><dd>{{ age() }} años <span class="muted">({{ q.birthDate | date: 'dd/MM/yyyy' }})</span></dd>
            <dt>Sexo</dt><dd>{{ L.sex[q.sex] }}</dd>
            <dt>Altura · peso</dt><dd>{{ q.heightCm }} cm · {{ q.weightKg | number: '1.0-1' }} kg <span class="muted">(IMC {{ bmi() | number: '1.1-1' }})</span></dd>
            @if (q.occupation) { <dt>Profesión</dt><dd>{{ q.occupation }}</dd> }
            <dt>Actividad diaria</dt><dd>{{ L.activityLevel[q.activityLevel] }}</dd>
          </dl>
        </section>
        <section>
          <h4><mat-icon>flag</mat-icon> Objetivo</h4>
          <dl>
            <dt>Objetivo</dt><dd><strong>{{ L.mainGoal[q.mainGoal] }}</strong></dd>
            @if (q.goalDetails) { <dt>Detalle</dt><dd>{{ q.goalDetails }}</dd> }
            <dt>Experiencia</dt><dd>{{ L.experience[q.experience] }}</dd>
            @if (q.currentTraining) { <dt>Ahora hace</dt><dd>{{ q.currentTraining }}</dd> }
          </dl>
        </section>
        <section>
          <h4><mat-icon>health_and_safety</mat-icon> Salud</h4>
          <dl>
            <dt>Lesiones</dt><dd [class.flag]="q.hasInjuries">{{ q.hasInjuries ? q.injuries : 'No' }}</dd>
            <dt>Condiciones</dt><dd [class.flag]="q.hasMedicalConditions">{{ q.hasMedicalConditions ? q.medicalConditions : 'No' }}</dd>
            <dt>Medicación</dt><dd>{{ q.medication || 'No indica' }}</dd>
            <dt>Operaciones</dt><dd>{{ q.surgeries || 'No indica' }}</dd>
            <dt>Analítica &lt; 1 año</dt><dd>{{ q.recentBloodTest === true ? 'Sí' : q.recentBloodTest === false ? 'No' : '—' }}</dd>
          </dl>
        </section>
        <section>
          <h4><mat-icon>fitness_center</mat-icon> Entrenamiento</h4>
          <dl>
            <dt>Disponibilidad</dt><dd>{{ q.daysPerWeek }} días · {{ q.minutesPerSession }} min</dd>
            <dt>Lugar</dt><dd>{{ L.trainingPlace[q.trainingPlace] }}</dd>
            @if (q.equipment) { <dt>Material</dt><dd>{{ q.equipment }}</dd> }
          </dl>
        </section>
        <section>
          <h4><mat-icon>restaurant</mat-icon> Alimentación y hábitos</h4>
          <dl>
            <dt>Alimentación</dt><dd>{{ L.dietType[q.dietType] }}@if (q.mealsPerDay) { · {{ q.mealsPerDay }} comidas }</dd>
            <dt>Alergias</dt><dd [class.flag]="!!q.allergies">{{ q.allergies || 'Ninguna' }}</dd>
            @if (q.dislikedFoods) { <dt>No le gusta</dt><dd>{{ q.dislikedFoods }}</dd> }
            <dt>Sueño · estrés</dt><dd>{{ q.sleepHours ?? '—' }} h · estrés {{ q.stressLevel ?? '—' }}/5</dd>
            @if (q.alcoholTobacco) { <dt>Alcohol / tabaco</dt><dd>{{ q.alcoholTobacco }}</dd> }
          </dl>
        </section>
        <section>
          <h4><mat-icon>workspace_premium</mat-icon> Plan</h4>
          <dl>
            <dt>Plan elegido</dt><dd><strong>{{ planName() || 'Sin decidir' }}</strong>@if (q.billingPeriod && planName()) { · pago {{ periodLabel[q.billingPeriod].toLowerCase() }} }</dd>
            @if (q.howFound) { <dt>Nos conoció</dt><dd>{{ q.howFound }}</dd> }
            @if (q.comments) { <dt>Comentarios</dt><dd>{{ q.comments }}</dd> }
          </dl>
        </section>
      </div>
    }
  `,
  styles: `
    :host { display: grid; gap: 14px; }
    .health { align-items: flex-start; }
    .health p { margin: 4px 0 0; }
    .sections { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
    section { border: 1px solid var(--border); border-radius: 14px; padding: 14px 16px; background: var(--surface-2); min-width: 0; }
    h4 { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: .92rem; }
    h4 mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--brand); }
    dl { margin: 0; display: grid; grid-template-columns: minmax(96px, auto) 1fr; gap: 6px 12px; font-size: .88rem; }
    dt { color: var(--text-subtle); }
    dd { margin: 0; overflow-wrap: anywhere; white-space: pre-line; }
    dd.flag { color: var(--warning-fg); font-weight: 600; }
    .muted { color: var(--text-subtle); }
  `,
})
export class QuestionnaireView {
  readonly q = input<Questionnaire | null>(null);
  readonly planName = input<string | null>(null);
  readonly L = Q_LABELS;
  readonly periodLabel = BILLING_PERIOD_LABEL;

  readonly age = computed(() => {
    const b = this.q()?.birthDate;
    if (!b) return '—';
    const d = new Date(b);
    const now = new Date();
    let a = now.getFullYear() - d.getFullYear();
    if (now.getMonth() < d.getMonth() || (now.getMonth() === d.getMonth() && now.getDate() < d.getDate())) a--;
    return a;
  });

  readonly bmi = computed(() => {
    const q = this.q();
    return q?.heightCm && q.weightKg ? q.weightKg / Math.pow(q.heightCm / 100, 2) : 0;
  });
}
