import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { DashboardService } from '../../../core/services/dashboard.service';
import { NotifyService } from '../../../core/services/notify.service';
import { VideoService } from '../../../core/services/video.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { VideoPlayer } from '../../../shared/components/video-player';
import { VideoUploadDialog } from '../../../shared/components/video-upload-dialog';
import { TechniqueVideo } from '../../../shared/models/video.model';
import { apiErrorMessage } from '../../../shared/utils/download';

type Filter = 'ALL' | 'MINE' | 'TRAINER';

/** Technique videos: send your execution for correction and watch the trainer's demonstrations. */
@Component({
  selector: 'app-client-videos',
  imports: [DatePipe, MatIconModule, VideoPlayer, VideoUploadDialog],
  templateUrl: './client-videos.html',
  styleUrl: './client-videos.scss',
})
export class ClientVideos implements OnInit {
  private readonly videoService = inject(VideoService);
  private readonly workoutService = inject(WorkoutService);
  private readonly notify = inject(NotifyService);
  private readonly dashboard = inject(DashboardService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly videos = signal<TechniqueVideo[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly filter = signal<Filter>('ALL');
  readonly exercises = signal<string[]>([]);
  readonly showUpload = signal(false);
  readonly initialExercise = signal<string | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly mineCount = computed(() => this.videos().filter((v) => v.source === 'CLIENT').length);
  readonly trainerCount = computed(() => this.videos().filter((v) => v.source === 'TRAINER').length);
  readonly pendingCount = computed(() => this.videos().filter((v) => v.source === 'CLIENT' && !v.reviewedAt).length);
  readonly visible = computed(() => {
    const f = this.filter();
    return this.videos().filter((v) => f === 'ALL' || (f === 'MINE' ? v.source === 'CLIENT' : v.source === 'TRAINER'));
  });

  ngOnInit(): void {
    this.load();
    this.workoutService.getMyWorkout().subscribe({
      next: (cw) => this.exercises.set([...new Set((cw?.workout?.exercises ?? []).map((e) => e.exerciseName))]),
      error: () => undefined,
    });
    // Deep link from "Mi rutina": /client/videos?exercise=Sentadilla opens the form prefilled.
    const exercise = this.route.snapshot.queryParamMap.get('exercise');
    if (exercise) {
      this.openUpload(exercise);
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.videoService.findMine().subscribe({
      next: (list) => {
        this.videos.set(list);
        this.loading.set(false);
        // Highlight stays for this visit; next time they are no longer "new".
        if (list.some((v) => v.unseen)) {
          this.videoService.markSeen().subscribe({ next: () => this.dashboard.notifyChanged(), error: () => undefined });
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(apiErrorMessage(err, 'No se pudieron cargar tus vídeos.'));
      },
    });
  }

  openUpload(exercise: string | null = null): void {
    this.initialExercise.set(exercise);
    this.showUpload.set(true);
  }

  onUploaded(video: TechniqueVideo): void {
    this.showUpload.set(false);
    this.videos.update((list) => [video, ...list]);
    this.filter.set('ALL');
    this.notify.success('Vídeo enviado. Tu entrenador te dirá cómo mejorar la técnica.');
  }

  remove(video: TechniqueVideo): void {
    if (!confirm(`¿Eliminar el vídeo de «${video.exerciseName}»?`)) return;
    this.deletingId.set(video.id);
    this.videoService.delete(video.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.videos.update((list) => list.filter((v) => v.id !== video.id));
        this.notify.success('Vídeo eliminado');
      },
      error: (err) => {
        this.deletingId.set(null);
        this.notify.error(err, 'No se pudo eliminar el vídeo.');
      },
    });
  }
}
