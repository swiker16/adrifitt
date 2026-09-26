import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { PhotoService } from '../../../core/services/photo.service';
import { NotifyService } from '../../../core/services/notify.service';
import { SecureImg } from '../../../shared/components/secure-img';
import { apiErrorMessage, isoDate } from '../../../shared/utils/download';
import { POSE_LABEL, PhotoPose, ProgressPhoto } from '../../../shared/models/photo.model';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];

/** Pose filter, or 'REPORT' = photos sent with a check-in (seguimiento). */
type PhotoFilter = PhotoPose | 'ALL' | 'REPORT';

interface PhotoGroup {
  date: string;
  photos: ProgressPhoto[];
}

@Component({
  selector: 'app-client-photos',
  imports: [MatIconModule, DatePipe, SecureImg],
  templateUrl: './client-photos.html',
  styleUrl: './client-photos.scss',
})
export class ClientPhotos implements OnDestroy {
  private readonly photoService = inject(PhotoService);
  private readonly notify = inject(NotifyService);

  readonly today = isoDate();
  readonly poseLabel = POSE_LABEL;
  readonly poses = Object.keys(POSE_LABEL) as PhotoPose[];

  // ── Gallery ─────────────────────────────────────────────────────────────
  readonly photos = signal<ProgressPhoto[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly poseFilter = signal<PhotoFilter>('ALL');
  readonly deletingId = signal<number | null>(null);

  // ── Upload form ─────────────────────────────────────────────────────────
  readonly showForm = signal(false);
  readonly file = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly takenOn = signal(isoDate());
  readonly pose = signal<PhotoPose>('FRONT');
  readonly notes = signal('');
  readonly fileError = signal<string | null>(null);
  readonly uploadError = signal<string | null>(null);
  readonly uploading = signal(false);

  // ── Viewer / compare ────────────────────────────────────────────────────
  readonly viewing = signal<ProgressPhoto | null>(null);
  readonly compareMode = signal(false);
  readonly compareIds = signal<number[]>([]);
  readonly showCompare = signal(false);

  readonly filtered = computed(() => {
    const f = this.poseFilter();
    return this.photos().filter((p) => this.matches(p, f));
  });

  readonly groups = computed<PhotoGroup[]>(() => {
    const map = new Map<string, ProgressPhoto[]>();
    for (const p of this.filtered()) {
      if (!map.has(p.takenOn)) map.set(p.takenOn, []);
      map.get(p.takenOn)!.push(p);
    }
    return [...map.entries()]
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([date, photos]) => ({ date, photos }));
  });

  readonly comparePhotos = computed(() => {
    const ids = this.compareIds();
    return ids
      .map((id) => this.photos().find((p) => p.id === id))
      .filter((p): p is ProgressPhoto => !!p)
      .sort((a, b) => a.takenOn.localeCompare(b.takenOn));
  });

  readonly dateError = computed(() => {
    const d = this.takenOn();
    if (!d) return 'Indica la fecha de la foto.';
    if (d > this.today) return 'La fecha no puede ser futura.';
    return null;
  });

  constructor() {
    this.load();
  }

  ngOnDestroy(): void {
    this.clearPreview();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.photoService.findMine().subscribe({
      next: (list) => {
        this.photos.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(apiErrorMessage(err, 'No se pudieron cargar tus fotos.'));
        this.loading.set(false);
      },
    });
  }

  readonly reportCount = computed(() => this.photos().filter((p) => p.reportId != null).length);

  countFor(filter: PhotoFilter): number {
    return this.photos().filter((p) => this.matches(p, filter)).length;
  }

  private matches(p: ProgressPhoto, f: PhotoFilter): boolean {
    if (f === 'ALL') return true;
    if (f === 'REPORT') return p.reportId != null;
    return p.pose === f;
  }

  /** Label shown on a photo: "Seguimiento" for check-in photos, the pose otherwise. */
  labelOf(p: ProgressPhoto): string {
    return p.reportId != null ? 'Seguimiento' : this.poseLabel[p.pose];
  }

  // ── Upload ──────────────────────────────────────────────────────────────

  openForm(): void {
    this.showForm.set(true);
    this.uploadError.set(null);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.resetForm();
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    this.fileError.set(null);
    this.uploadError.set(null);
    this.clearPreview();
    this.file.set(null);
    if (!f) return;
    if (!ACCEPTED.includes(f.type)) {
      this.fileError.set('Formato no admitido. Usa JPG, PNG o WEBP.');
      input.value = '';
      return;
    }
    if (f.size > MAX_BYTES) {
      this.fileError.set(`La foto pesa ${(f.size / 1024 / 1024).toFixed(1)} MB. El máximo es 10 MB.`);
      input.value = '';
      return;
    }
    this.file.set(f);
    this.previewUrl.set(URL.createObjectURL(f));
  }

  onDate(event: Event): void {
    this.takenOn.set((event.target as HTMLInputElement).value);
  }

  onPose(event: Event): void {
    this.pose.set((event.target as HTMLSelectElement).value as PhotoPose);
  }

  onNotes(event: Event): void {
    this.notes.set((event.target as HTMLTextAreaElement).value);
  }

  upload(): void {
    const f = this.file();
    if (!f) {
      this.fileError.set('Selecciona una foto.');
      return;
    }
    if (this.dateError() || this.uploading()) return;
    this.uploading.set(true);
    this.uploadError.set(null);
    this.photoService.upload(f, this.takenOn(), this.pose(), this.notes().trim() || null).subscribe({
      next: (photo) => {
        this.uploading.set(false);
        this.photos.update((list) => [photo, ...list]);
        this.notify.success('Foto subida correctamente');
        this.closeForm();
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(apiErrorMessage(err, 'No se pudo subir la foto.'));
      },
    });
  }

  private resetForm(): void {
    this.clearPreview();
    this.file.set(null);
    this.takenOn.set(isoDate());
    this.pose.set('FRONT');
    this.notes.set('');
    this.fileError.set(null);
    this.uploadError.set(null);
  }

  private clearPreview(): void {
    const url = this.previewUrl();
    if (url) URL.revokeObjectURL(url);
    this.previewUrl.set(null);
  }

  // ── Gallery actions ─────────────────────────────────────────────────────

  onPhotoClick(p: ProgressPhoto): void {
    if (this.compareMode()) this.toggleCompare(p.id);
    else this.viewing.set(p);
  }

  remove(p: ProgressPhoto, event?: Event): void {
    event?.stopPropagation();
    if (!confirm('¿Eliminar esta foto? No se puede deshacer.')) return;
    this.deletingId.set(p.id);
    this.photoService.deleteMine(p.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.photos.update((list) => list.filter((x) => x.id !== p.id));
        this.compareIds.update((ids) => ids.filter((id) => id !== p.id));
        if (this.viewing()?.id === p.id) this.viewing.set(null);
        this.notify.success('Foto eliminada');
      },
      error: (err) => {
        this.deletingId.set(null);
        this.notify.error(err, 'No se pudo eliminar la foto.');
      },
    });
  }

  // ── Compare ─────────────────────────────────────────────────────────────

  toggleCompareMode(): void {
    this.compareMode.update((v) => !v);
    this.compareIds.set([]);
  }

  toggleCompare(id: number): void {
    this.compareIds.update((ids) => {
      if (ids.includes(id)) return ids.filter((x) => x !== id);
      if (ids.length >= 2) return [ids[1], id];
      return [...ids, id];
    });
  }

  isSelected(id: number): boolean {
    return this.compareIds().includes(id);
  }
}
