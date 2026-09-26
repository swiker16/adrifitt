import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { WorkoutLogService } from '../../../core/services/workout-log.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage, isoDate } from '../../../shared/utils/download';
import {
  LogSetRequest,
  LogTemplate,
  SaveWorkoutLogRequest,
  SetValue,
  TemplateExercise,
  WorkoutDay,
  WorkoutLog,
} from '../../../shared/models/workout-log.model';

/** Editable state of one set row. Inputs are kept as raw strings and parsed on validation. */
interface RowForm {
  setNumber: number;
  weight: string;
  reps: string;
  rir: string;
  done: boolean;
}

interface ExerciseForm {
  ex: TemplateExercise;
  rows: RowForm[];
}

interface RowErrors {
  weight?: string;
  reps?: string;
  rir?: string;
}

type Tab = 'log' | 'history';

@Component({
  selector: 'app-client-workout-log',
  imports: [MatIconModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './client-workout-log.html',
  styleUrl: './client-workout-log.scss',
})
export class ClientWorkoutLog {
  private readonly logService = inject(WorkoutLogService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);

  readonly today = isoDate();
  readonly rirOptions = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  // ── Page state ──────────────────────────────────────────────────────────
  readonly tab = signal<Tab>('log');
  readonly loadingDays = signal(true);
  readonly noRoutine = signal(false);
  readonly daysError = signal<string | null>(null);
  readonly days = signal<WorkoutDay[]>([]);

  // ── Form state ──────────────────────────────────────────────────────────
  readonly performedOn = signal(isoDate());
  readonly selectedDay = signal<number | null>(null);
  readonly template = signal<LogTemplate | null>(null);
  readonly loadingTemplate = signal(false);
  readonly templateError = signal<string | null>(null);
  readonly exercises = signal<ExerciseForm[]>([]);
  readonly notes = signal('');
  readonly attempted = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  /** Existing log that caused a 409 on create (same day already logged). */
  readonly conflictLog = signal<WorkoutLog | null>(null);
  readonly editingId = signal<number | null>(null);
  readonly editingDropped = signal(0);

  // ── History ─────────────────────────────────────────────────────────────
  readonly history = signal<WorkoutLog[]>([]);
  readonly loadingHistory = signal(true);
  readonly historyError = signal<string | null>(null);
  readonly expandedId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly totalRows = computed(() => this.exercises().reduce((n, e) => n + e.rows.length, 0));
  readonly doneRows = computed(() => this.exercises().reduce((n, e) => n + e.rows.filter((r) => r.done).length, 0));

  /** Errors per [exerciseIndex][rowIndex]. */
  readonly errors = computed<RowErrors[][]>(() =>
    this.exercises().map((e) => e.rows.map((r) => this.validateRow(r))),
  );

  readonly dateError = computed(() => {
    const d = this.performedOn();
    if (!d) return 'Indica la fecha del entreno.';
    if (d > this.today) return 'La fecha no puede ser futura.';
    return null;
  });

  readonly hasErrors = computed(() => this.errors().some((rows) => rows.some((e) => Object.keys(e).length > 0)));

  readonly volumePreview = computed(() => {
    let v = 0;
    for (const e of this.exercises()) {
      for (const r of e.rows) {
        const w = this.num(r.weight);
        const reps = this.num(r.reps);
        if (r.done && w !== null && reps !== null) v += w * reps;
      }
    }
    return v;
  });

  private readonly requestedDay: number | null;

  constructor() {
    const q = Number(this.route.snapshot.queryParamMap.get('day'));
    this.requestedDay = Number.isInteger(q) && q > 0 ? q : null;
    this.loadDays();
    this.loadHistory();
  }

  // ── Loading ─────────────────────────────────────────────────────────────

  private loadDays(): void {
    this.loadingDays.set(true);
    this.logService.myDays().subscribe({
      next: (days) => {
        this.days.set([...days].sort((a, b) => a.dayNumber - b.dayNumber));
        this.noRoutine.set(days.length === 0);
        this.loadingDays.set(false);
        if (this.requestedDay && days.some((d) => d.dayNumber === this.requestedDay)) {
          this.selectDay(this.requestedDay);
        }
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) this.noRoutine.set(true);
        else this.daysError.set(apiErrorMessage(err, 'No se pudieron cargar los días de tu rutina.'));
        this.loadingDays.set(false);
      },
    });
  }

  loadHistory(): void {
    this.loadingHistory.set(true);
    this.historyError.set(null);
    this.logService.findMine().subscribe({
      next: (logs) => {
        this.history.set(this.sortLogs(logs));
        this.loadingHistory.set(false);
      },
      error: (err) => {
        this.historyError.set(apiErrorMessage(err, 'No se pudo cargar el historial.'));
        this.loadingHistory.set(false);
      },
    });
  }

  private sortLogs(logs: WorkoutLog[]): WorkoutLog[] {
    return [...logs].sort((a, b) =>
      a.performedOn === b.performedOn ? b.id - a.id : b.performedOn.localeCompare(a.performedOn),
    );
  }

  // ── Step 1: day selection ───────────────────────────────────────────────

  selectDay(dayNumber: number, fill?: WorkoutLog): void {
    this.selectedDay.set(dayNumber);
    this.template.set(null);
    this.exercises.set([]);
    this.templateError.set(null);
    this.saveError.set(null);
    this.conflictLog.set(null);
    this.attempted.set(false);
    this.loadingTemplate.set(true);
    this.logService.myTemplate(dayNumber).subscribe({
      next: (tpl) => {
        this.template.set(tpl);
        this.exercises.set(
          tpl.exercises.map((ex) => ({
            ex,
            rows: Array.from({ length: Math.max(1, ex.sets) }, (_, i) => ({
              setNumber: i + 1,
              weight: '',
              reps: '',
              rir: '',
              done: false,
            })),
          })),
        );
        if (fill) this.fillFromLog(fill);
        this.loadingTemplate.set(false);
        this.scrollTop();
      },
      error: (err) => {
        this.templateError.set(apiErrorMessage(err, 'No se pudo cargar la plantilla de este día.'));
        this.loadingTemplate.set(false);
      },
    });
  }

  backToDays(): void {
    if (this.doneRows() > 0 && !confirm('¿Descartar los datos introducidos?')) return;
    this.resetForm();
  }

  private resetForm(): void {
    this.selectedDay.set(null);
    this.template.set(null);
    this.exercises.set([]);
    this.notes.set('');
    this.attempted.set(false);
    this.saveError.set(null);
    this.conflictLog.set(null);
    this.editingId.set(null);
    this.editingDropped.set(0);
    this.performedOn.set(isoDate());
  }

  // ── Step 2: set rows ────────────────────────────────────────────────────

  patchRow(ei: number, ri: number, patch: Partial<RowForm>): void {
    this.exercises.update((list) =>
      list.map((e, i) =>
        i !== ei
          ? e
          : {
              ...e,
              rows: e.rows.map((r, j) => {
                if (j !== ri) return r;
                const next = { ...r, ...patch };
                // Auto-mark as done once weight and RIR are filled (user can still untick it).
                if (!('done' in patch) && !r.done && next.weight.trim() !== '' && next.rir !== '') next.done = true;
                return next;
              }),
            },
      ),
    );
  }

  onInput(ei: number, ri: number, field: 'weight' | 'reps', event: Event): void {
    this.patchRow(ei, ri, { [field]: (event.target as HTMLInputElement).value });
  }

  onRir(ei: number, ri: number, event: Event): void {
    this.patchRow(ei, ri, { rir: (event.target as HTMLSelectElement).value });
  }

  toggleDone(ei: number, ri: number, event: Event): void {
    this.patchRow(ei, ri, { done: (event.target as HTMLInputElement).checked });
  }

  onNotes(event: Event): void {
    this.notes.set((event.target as HTMLTextAreaElement).value);
  }

  onDate(event: Event): void {
    this.performedOn.set((event.target as HTMLInputElement).value);
  }

  previousFor(ex: TemplateExercise, setNumber: number): SetValue | undefined {
    return ex.previous?.find((p) => p.setNumber === setNumber);
  }

  previousText(p: SetValue | undefined): string {
    if (!p) return '';
    return `${this.fmt(p.weightKg)} kg${p.reps != null ? ' × ' + p.reps : ''} @RIR${p.rir}`;
  }

  copyPrevious(ei: number): void {
    this.exercises.update((list) =>
      list.map((e, i) => {
        if (i !== ei) return e;
        return {
          ...e,
          rows: e.rows.map((r) => {
            const p = this.previousFor(e.ex, r.setNumber);
            if (!p) return r;
            return {
              ...r,
              weight: String(p.weightKg),
              reps: p.reps != null ? String(p.reps) : '',
              rir: String(p.rir),
              done: true,
            };
          }),
        };
      }),
    );
  }

  targetText(ex: TemplateExercise): string {
    const reps = ex.targetReps != null ? String(ex.targetReps) : ex.approxReps || '—';
    return `${ex.sets} × ${reps}`;
  }

  restText(seconds: number | null): string {
    if (seconds == null) return '';
    if (seconds < 60) return `${seconds}"`;
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return s ? `${m}'${String(s).padStart(2, '0')}"` : `${m}'`;
  }

  private validateRow(r: RowForm): RowErrors {
    const e: RowErrors = {};
    const w = this.num(r.weight);
    const reps = this.num(r.reps);
    if (r.weight.trim() !== '' && (w === null || w < 0 || w > 1000)) e.weight = 'Peso entre 0 y 1000 kg';
    if (r.reps.trim() !== '' && (reps === null || reps < 0 || reps > 200 || !Number.isInteger(reps)))
      e.reps = 'Reps: entero entre 0 y 200';
    if (r.rir !== '') {
      const rir = Number(r.rir);
      if (!Number.isInteger(rir) || rir < 0 || rir > 10) e.rir = 'RIR entre 0 y 10';
    }
    if (r.done) {
      if (r.weight.trim() === '') e.weight = 'Indica el peso';
      if (r.rir === '') e.rir = 'Indica el RIR';
    }
    return e;
  }

  private num(v: string): number | null {
    if (v == null || String(v).trim() === '') return null;
    const n = Number(String(v).replace(',', '.'));
    return Number.isFinite(n) ? n : null;
  }

  // ── Save ────────────────────────────────────────────────────────────────

  save(): void {
    if (this.saving()) return;
    this.attempted.set(true);
    this.saveError.set(null);
    this.conflictLog.set(null);
    const day = this.selectedDay();
    if (day === null) return;
    if (this.dateError()) {
      this.saveError.set(this.dateError());
      return;
    }
    if (this.hasErrors()) {
      this.saveError.set('Revisa las series marcadas en rojo.');
      return;
    }
    const sets: LogSetRequest[] = [];
    for (const e of this.exercises()) {
      for (const r of e.rows) {
        if (!r.done) continue;
        sets.push({
          exerciseId: e.ex.exerciseId,
          setNumber: r.setNumber,
          weightKg: this.num(r.weight)!,
          reps: this.num(r.reps),
          rir: Number(r.rir),
        });
      }
    }
    if (sets.length === 0) {
      this.saveError.set('Marca como hecha al menos una serie (con peso y RIR).');
      return;
    }
    const request: SaveWorkoutLogRequest = {
      performedOn: this.performedOn(),
      dayNumber: day,
      notes: this.notes().trim() || null,
      sets,
    };
    const editing = this.editingId();
    this.saving.set(true);
    const call = editing ? this.logService.update(editing, request) : this.logService.create(request);
    call.subscribe({
      next: (log) => {
        this.saving.set(false);
        this.notify.success(editing ? 'Entreno actualizado' : '¡Entreno registrado!');
        this.history.update((h) => this.sortLogs([log, ...h.filter((x) => x.id !== log.id)]));
        this.resetForm();
        this.expandedId.set(log.id);
        this.tab.set('history');
        this.scrollTop();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        const msg = apiErrorMessage(err, 'No se pudo guardar el entreno.');
        this.saveError.set(msg);
        if (err.status === 409 && !editing) {
          const existing = this.history().find(
            (l) => l.performedOn === request.performedOn && l.dayNumber === request.dayNumber,
          ) ?? this.history().find((l) => l.performedOn === request.performedOn);
          this.conflictLog.set(existing ?? null);
        }
        this.notify.error(msg);
      },
    });
  }

  // ── History actions ─────────────────────────────────────────────────────

  toggleExpand(id: number): void {
    this.expandedId.update((cur) => (cur === id ? null : id));
  }

  edit(log: WorkoutLog): void {
    if (this.doneRows() > 0 && this.editingId() !== log.id && !confirm('Tienes datos sin guardar. ¿Descartarlos?')) return;
    this.resetForm();
    this.editingId.set(log.id);
    this.performedOn.set(log.performedOn);
    this.notes.set(log.notes ?? '');
    this.tab.set('log');
    this.selectDay(log.dayNumber, log);
  }

  cancelEdit(): void {
    this.resetForm();
  }

  private fillFromLog(log: WorkoutLog): void {
    let dropped = 0;
    const byExercise = new Map<number, SetValue[]>();
    for (const le of log.exercises) {
      if (le.exerciseId == null) {
        dropped += le.sets.length;
        continue;
      }
      byExercise.set(le.exerciseId, le.sets);
    }
    const used = new Set<string>();
    this.exercises.update((list) =>
      list.map((e) => {
        const logged = byExercise.get(e.ex.exerciseId) ?? [];
        return {
          ...e,
          rows: e.rows.map((r) => {
            const s = logged.find((x) => x.setNumber === r.setNumber);
            if (!s) return r;
            used.add(`${e.ex.exerciseId}-${s.setNumber}`);
            return {
              ...r,
              weight: String(s.weightKg),
              reps: s.reps != null ? String(s.reps) : '',
              rir: String(s.rir),
              done: true,
            };
          }),
        };
      }),
    );
    for (const [id, sets] of byExercise) {
      dropped += sets.filter((s) => !used.has(`${id}-${s.setNumber}`)).length;
    }
    this.editingDropped.set(dropped);
    this.notes.set(log.notes ?? '');
    this.performedOn.set(log.performedOn);
  }

  openConflict(): void {
    const log = this.conflictLog();
    if (!log) return;
    this.editingId.set(null);
    this.exercises.set([]);
    this.edit(log);
  }

  remove(log: WorkoutLog): void {
    if (!confirm(`¿Eliminar el entreno del ${this.fmtDate(log.performedOn)} (${log.dayName})?`)) return;
    this.deletingId.set(log.id);
    this.logService.delete(log.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.history.update((h) => h.filter((x) => x.id !== log.id));
        if (this.editingId() === log.id) this.resetForm();
        this.notify.success('Entreno eliminado');
      },
      error: (err) => {
        this.deletingId.set(null);
        this.notify.error(err, 'No se pudo eliminar el entreno.');
      },
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private fmt(n: number): string {
    return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/\.?0+$/, '').replace('.', ',');
  }

  private fmtDate(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  private scrollTop(): void {
    if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
