import { HttpClient, HttpEvent } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { TechniqueVideo, VideoStreamLink, VideoUpload } from '../../shared/models/video.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class VideoService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Client ──────────────────────────────────────────────────────────────

  /** Upload with progress events (videos are big: the UI shows a progress bar). */
  uploadMine(upload: VideoUpload): Observable<HttpEvent<TechniqueVideo>> {
    return this.http.post<TechniqueVideo>(`${this.base}/videos/me`, this.form(upload), {
      reportProgress: true,
      observe: 'events',
    });
  }

  findMine(): Observable<TechniqueVideo[]> {
    return this.http.get<TechniqueVideo[]>(`${this.base}/videos/me`);
  }

  markSeen(): Observable<void> {
    return this.http.post<void>(`${this.base}/videos/me/seen`, {});
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  findAll(pendingOnly = false): Observable<TechniqueVideo[]> {
    return this.http.get<TechniqueVideo[]>(`${this.base}/videos`, { params: { pending: pendingOnly } });
  }

  findForClient(clientId: number): Observable<TechniqueVideo[]> {
    return this.http.get<TechniqueVideo[]>(`${this.base}/clients/${clientId}/videos`);
  }

  uploadForClient(clientId: number, upload: VideoUpload): Observable<HttpEvent<TechniqueVideo>> {
    return this.http.post<TechniqueVideo>(`${this.base}/clients/${clientId}/videos`, this.form(upload), {
      reportProgress: true,
      observe: 'events',
    });
  }

  review(id: number, feedback: string): Observable<TechniqueVideo> {
    return this.http.patch<TechniqueVideo>(`${this.base}/videos/${id}/feedback`, { feedback });
  }

  // ── Shared ──────────────────────────────────────────────────────────────

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/videos/${id}`);
  }

  /** Short-lived signed URL that a <video> element can stream (no auth header needed). */
  link(id: number): Observable<VideoStreamLink> {
    return this.http.get<VideoStreamLink>(`${this.base}/videos/${id}/link`);
  }

  private form(upload: VideoUpload): FormData {
    const fd = new FormData();
    fd.append('file', upload.file);
    fd.append('exerciseName', upload.exerciseName);
    if (upload.note) fd.append('note', upload.note);
    if (upload.durationSeconds) fd.append('durationSeconds', String(Math.round(upload.durationSeconds)));
    return fd;
  }
}
