import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ClientService } from '../../../core/services/client.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { NotifyService } from '../../../core/services/notify.service';
import { VideoService } from '../../../core/services/video.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { VideoPlayer } from '../../../shared/components/video-player';
import { VideoClientOption, VideoUploadDialog } from '../../../shared/components/video-upload-dialog';
import { TechniqueVideo } from '../../../shared/models/video.model';
import { apiErrorMessage } from '../../../shared/utils/download';

type Filter = 'PENDING' | 'ALL';

/** Trainer inbox of technique videos: correct the clients' executions and send demonstrations. */
@Component({
  selector: 'app-trainer-videos',
  imports: [DatePipe, FormsModule, RouterLink, MatIconModule, VideoPlayer, VideoUploadDialog],
  templateUrl: './trainer-videos.html',
  styleUrl: './trainer-videos.scss',
})
export class TrainerVideos implements OnInit {
  private readonly videoService = inject(VideoService);
  private readonly clientService = inject(ClientService);
  private readonly workoutService = inject(WorkoutService);
  private readonly notify = inject(NotifyService);
  private readonly dashboard = inject(DashboardService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly videos = signal<TechniqueVideo[]>([]);
  readonly clients = signal<VideoClientOption[]>([]);
  readonly exercises = signal<string[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly filter = signal<Filter>('PENDING');
  readonly clientFilter = signal<number | null>(null);
  readonly showUpload = signal(false);

  /** Correction drafts and the card being edited / saved. */
  readonly drafts = signal<Record<number, string>>({});
  readonly editing = signal<number | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly pendingCount = computed(() => this.forClient().filter((v) => this.isPending(v)).length);
  readonly visible = computed(() => {
    const list = this.forClient();
    return this.filter() === 'PENDING'
      // Oldest pending first: the client has been waiting longest.
      ? list.filter((v) => this.isPending(v)).sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      : list;
  });

  private readonly forClient = computed(() => {
    const id = this.clientFilter();
    return id == null ? this.videos() : this.videos().filter((v) => v.clientId === id);
  });

  ngOnInit(): void {
    const clientId = Number(this.route.snapshot.queryParamMap.get('clientId'));
    if (clientId) this.clientFilter.set(clientId);
    this.load();
    this.clientService.findAll().subscribe({
      next: (list) => this.clients.set(
        list.map((c) => ({ id: c.id, name: `${c.firstName} ${c.lastName}`.trim() }))
          .sort((a, b) => a.name.localeCompare(b.name, 'es'))),
      error: () => undefined,
    });
    this.workoutService.findAll().subscribe({
      next: (list) => this.exercises.set(
        [...new Set(list.flatMap((w) => w.exercises.map((e) => e.exerciseName)))].sort((a, b) => a.localeCompare(b, 'es'))),
      error: () => undefined,
    });
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.videoService.findAll(false).subscribe({
      next: (list) => {
        this.videos.set(list);
        this.loading.set(false);
        if (this.filter() === 'PENDING' && !list.some((v) => this.isPending(v) && (this.clientFilter() == null || v.clientId === this.clientFilter()))) {
          this.filter.set('ALL');
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(apiErrorMessage(err, 'No se pudieron cargar los vídeos.'));
      },
    });
  }

  isPending(v: TechniqueVideo): boolean {
    return v.source === 'CLIENT' && !v.reviewedAt;
  }

  setClientFilter(id: number | null): void {
    this.clientFilter.set(id);
    this.router.navigate([], { queryParams: { clientId: id ?? null }, replaceUrl: true });
  }

  draft(v: TechniqueVideo): string {
    return this.drafts()[v.id] ?? (this.editing() === v.id ? v.feedback ?? '' : '');
  }

  setDraft(v: TechniqueVideo, text: string): void {
    this.drafts.update((d) => ({ ...d, [v.id]: text }));
  }

  startEdit(v: TechniqueVideo): void {
    this.setDraft(v, v.feedback ?? '');
    this.editing.set(v.id);
  }

  saveFeedback(v: TechniqueVideo): void {
    const text = this.draft(v).trim();
    if (!text) return;
    this.savingId.set(v.id);
    this.videoService.review(v.id, text).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.editing.set(null);
        this.drafts.update((d) => {
          const { [v.id]: _, ...rest } = d;
          return rest;
        });
        this.videos.update((list) => list.map((x) => (x.id === v.id ? updated : x)));
        this.notify.success(`Corrección enviada a ${v.clientName ?? 'tu cliente'}`);
        this.dashboard.notifyChanged();
      },
      error: (err) => {
        this.savingId.set(null);
        this.notify.error(err, 'No se pudo enviar la corrección.');
      },
    });
  }

  remove(v: TechniqueVideo): void {
    if (!confirm(`¿Eliminar el vídeo «${v.exerciseName}» de ${v.clientName}?`)) return;
    this.videoService.delete(v.id).subscribe({
      next: () => {
        this.videos.update((list) => list.filter((x) => x.id !== v.id));
        this.notify.success('Vídeo eliminado');
        this.dashboard.notifyChanged();
      },
      error: (err) => this.notify.error(err, 'No se pudo eliminar el vídeo.'),
    });
  }

  onUploaded(video: TechniqueVideo): void {
    this.showUpload.set(false);
    this.videos.update((list) => [video, ...list]);
    this.filter.set('ALL');
    this.notify.success(`Vídeo enviado a ${video.clientName ?? 'tu cliente'}`);
  }
}
