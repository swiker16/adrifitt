import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { ReportService } from '../../../core/services/report.service';
import { WorkoutLogService } from '../../../core/services/workout-log.service';
import { PhotoService } from '../../../core/services/photo.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { LineChart, ChartPoint } from '../../../shared/components/line-chart';
import { BarChart, BarPoint } from '../../../shared/components/bar-chart';
import { SecureImg } from '../../../shared/components/secure-img';
import { WeeklyReport } from '../../../shared/models/report.model';
import { ExerciseProgress, ExerciseProgressPoint } from '../../../shared/models/workout-log.model';
import { POSE_LABEL, PhotoPose, ProgressPhoto } from '../../../shared/models/photo.model';
import { ClientDashboard } from '../../../shared/models/dashboard.model';
import { PhotoLightbox } from '../report/photo-lightbox';

interface Delta {
  from: number;
  to: number;
  diff: number;
}

type SecondaryMetric = 'energy' | 'diet' | 'training';

interface StrengthRow extends ExerciseProgressPoint {
  prWeight: boolean;
  prOneRm: boolean;
}

/** Wraps a request so a single failing endpoint doesn't break the whole page. */
function safe<T>(obs: Observable<T>, fallback: T): Observable<{ data: T; failed: boolean }> {
  return obs.pipe(
    map((data) => ({ data, failed: false })),
    catchError(() => of({ data: fallback, failed: true })),
  );
}

@Component({
  selector: 'app-client-progress',
  imports: [MatIconModule, DatePipe, DecimalPipe, RouterLink, LineChart, BarChart, SecureImg, PhotoLightbox],
  templateUrl: './client-progress.html',
  styleUrl: './client-progress.scss',
})
export class ClientProgress {
  private readonly reportService = inject(ReportService);
  private readonly logService = inject(WorkoutLogService);
  private readonly photoService = inject(PhotoService);
  private readonly dashboardService = inject(DashboardService);

  readonly poseLabel = POSE_LABEL;

  readonly loading = signal(true);
  readonly partialError = signal(false);
  readonly reports = signal<WeeklyReport[]>([]);
  readonly strength = signal<ExerciseProgress[]>([]);
  readonly photos = signal<ProgressPhoto[]>([]);
  readonly dashboard = signal<ClientDashboard | null>(null);

  readonly secondary = signal<SecondaryMetric>('energy');
  readonly selectedExercise = signal<string | null>(null);
  readonly selectedPose = signal<PhotoPose | null>(null);

  /** Reports oldest → newest. */
  readonly sortedReports = computed(() =>
    [...this.reports()].sort((a, b) => a.createdAt.localeCompare(b.createdAt)),
  );

  readonly weightDelta = computed<Delta | null>(() => {
    const r = this.sortedReports();
    const d = this.dashboard();
    const from = d?.startWeight ?? r[0]?.weight ?? null;
    const to = d?.currentWeight ?? r[r.length - 1]?.weight ?? null;
    return from != null && to != null ? { from, to, diff: to - from } : null;
  });

  readonly waistDelta = computed(() => this.deltaOf((r) => r.waist));
  readonly fatDelta = computed(() => this.deltaOf((r) => r.bodyFat));

  readonly weightPoints = computed(() => this.pointsOf((r) => r.weight));
  readonly waistPoints = computed(() => this.pointsOf((r) => r.waist));
  readonly fatPoints = computed(() => this.pointsOf((r) => r.bodyFat));

  /** Legacy metrics (only old check-ins have them): sections render only when there is data. */
  readonly hasWaist = computed(() => this.waistPoints().some((p) => p.value != null));
  readonly hasFat = computed(() => this.fatPoints().some((p) => p.value != null));
  readonly secondaryAvailable = computed<SecondaryMetric[]>(() => {
    const r = this.sortedReports();
    const out: SecondaryMetric[] = [];
    if (r.some((x) => x.energyLevel != null)) out.push('energy');
    if (r.some((x) => x.dietAdherence != null)) out.push('diet');
    if (r.some((x) => x.trainingAdherence != null)) out.push('training');
    return out;
  });
  /** Selected metric, falling back to the first one that has data. */
  readonly secondaryMetric = computed<SecondaryMetric>(() => {
    const avail = this.secondaryAvailable();
    return avail.includes(this.secondary()) ? this.secondary() : (avail[0] ?? 'energy');
  });

  /** Check-ins with photos, newest first (for the "Fotos de mis seguimientos" strip). */
  readonly reportsWithPhotos = computed(() =>
    [...this.sortedReports()].reverse().filter((r) => r.photos.length > 0).slice(0, 8),
  );

  readonly lbPhotos = signal<ProgressPhoto[]>([]);
  readonly lbTitle = signal('');
  readonly lbIndex = signal<number | null>(null);

  readonly secondaryPoints = computed<BarPoint[]>(() => {
    const metric = this.secondaryMetric();
    return this.sortedReports()
      .filter((r) => (metric === 'energy' ? r.energyLevel : metric === 'diet' ? r.dietAdherence : r.trainingAdherence) != null)
      .slice(-12)
      .map((r) => ({
        label: this.shortDate(r.createdAt),
        value:
          (metric === 'energy' ? r.energyLevel : metric === 'diet' ? r.dietAdherence : r.trainingAdherence) ?? 0,
      }));
  });

  readonly secondaryAvg = computed(() => {
    const pts = this.secondaryPoints().filter((p) => p.value > 0);
    return pts.length ? pts.reduce((s, p) => s + p.value, 0) / pts.length : null;
  });

  // ── Strength ────────────────────────────────────────────────────────────

  readonly exerciseOptions = computed(() =>
    [...this.strength()]
      .filter((e) => e.points.length > 0)
      .sort((a, b) => b.points.length - a.points.length || a.exerciseName.localeCompare(b.exerciseName)),
  );

  readonly currentExercise = computed<ExerciseProgress | null>(() => {
    const name = this.selectedExercise();
    const opts = this.exerciseOptions();
    return opts.find((e) => e.exerciseName === name) ?? (opts.length > 0 ? opts[0] : null);
  });

  /** Oldest → newest with PR flags. */
  private readonly strengthRowsAsc = computed<StrengthRow[]>(() => {
    const ex = this.currentExercise();
    if (!ex) return [];
    let bestW = -Infinity;
    let bestRm = -Infinity;
    return [...ex.points]
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((p, i) => {
        const prWeight = i > 0 && p.maxWeightKg > bestW;
        const rm = p.estimatedOneRepMaxKg ?? -Infinity;
        const prOneRm = i > 0 && p.estimatedOneRepMaxKg != null && rm > bestRm;
        bestW = Math.max(bestW, p.maxWeightKg);
        bestRm = Math.max(bestRm, rm);
        return { ...p, prWeight, prOneRm };
      });
  });

  readonly strengthRows = computed(() => [...this.strengthRowsAsc()].reverse());

  readonly maxWeightPoints = computed<ChartPoint[]>(() =>
    this.strengthRowsAsc().map((p) => ({ label: this.shortDate(p.date), value: p.maxWeightKg })),
  );

  readonly oneRmPoints = computed<ChartPoint[]>(() =>
    this.strengthRowsAsc().map((p) => ({ label: this.shortDate(p.date), value: p.estimatedOneRepMaxKg })),
  );

  readonly strengthSummary = computed(() => {
    const rows = this.strengthRowsAsc();
    if (rows.length === 0) return null;
    const best = Math.max(...rows.map((r) => r.maxWeightKg));
    const rms = rows.map((r) => r.estimatedOneRepMaxKg).filter((v): v is number => v != null);
    return {
      sessions: rows.length,
      best,
      bestRm: rms.length ? Math.max(...rms) : null,
      diff: rows[rows.length - 1].maxWeightKg - rows[0].maxWeightKg,
    };
  });

  // ── Before / after ──────────────────────────────────────────────────────

  readonly comparablePoses = computed<PhotoPose[]>(() => {
    const counts = new Map<PhotoPose, number>();
    for (const p of this.photos()) counts.set(p.pose, (counts.get(p.pose) ?? 0) + 1);
    const order: PhotoPose[] = ['FRONT', 'SIDE', 'BACK', 'OTHER'];
    return order.filter((p) => (counts.get(p) ?? 0) >= 2);
  });

  readonly beforeAfter = computed(() => {
    const poses = this.comparablePoses();
    const pose = poses.includes(this.selectedPose() as PhotoPose) ? this.selectedPose()! : poses[0];
    if (!pose) return null;
    const list = this.photos()
      .filter((p) => p.pose === pose)
      .sort((a, b) => a.takenOn.localeCompare(b.takenOn) || a.id - b.id);
    return { pose: pose as PhotoPose, before: list[0], after: list[list.length - 1] };
  });

  constructor() {
    forkJoin({
      reports: safe(this.reportService.findMine(), [] as WeeklyReport[]),
      strength: safe(this.logService.myProgress(), [] as ExerciseProgress[]),
      photos: safe(this.photoService.findMine(), [] as ProgressPhoto[]),
      dashboard: safe(this.dashboardService.getClientDashboard(), null as ClientDashboard | null),
    }).subscribe((res) => {
      this.reports.set(res.reports.data);
      this.strength.set(res.strength.data);
      this.photos.set(res.photos.data);
      this.dashboard.set(res.dashboard.data);
      this.partialError.set(
        res.reports.failed || res.strength.failed || res.photos.failed || res.dashboard.failed,
      );
      this.loading.set(false);
    });
  }

  openPhotos(r: WeeklyReport, index: number): void {
    this.lbPhotos.set(r.photos);
    this.lbTitle.set('Seguimiento del ' + this.shortDate(r.createdAt));
    this.lbIndex.set(index);
  }

  onExercise(event: Event): void {
    this.selectedExercise.set((event.target as HTMLSelectElement).value);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private deltaOf(pick: (r: WeeklyReport) => number | undefined | null): Delta | null {
    const vals = this.sortedReports()
      .map(pick)
      .filter((v): v is number => v != null);
    if (vals.length === 0) return null;
    const from = vals[0];
    const to = vals[vals.length - 1];
    return { from, to, diff: to - from };
  }

  private pointsOf(pick: (r: WeeklyReport) => number | undefined | null): ChartPoint[] {
    return this.sortedReports().map((r) => ({ label: this.shortDate(r.createdAt), value: pick(r) ?? null }));
  }

  /** dd/MM from an ISO date or date-time without timezone shifts for plain dates. */
  shortDate(iso: string): string {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
    if (m) return `${m[3]}/${m[2]}`;
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  /** CSS class for a delta where going down is good (weight, waist, fat). */
  trendClass(d: Delta | null): string {
    if (!d || Math.abs(d.diff) < 0.05) return 'neutral';
    return d.diff < 0 ? 'good' : 'bad';
  }
}
