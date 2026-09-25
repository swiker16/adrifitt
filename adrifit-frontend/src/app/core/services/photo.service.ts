import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PhotoPose, ProgressPhoto } from '../../shared/models/photo.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class PhotoService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Client ──────────────────────────────────────────────────────────────

  upload(file: File, takenOn: string, pose: PhotoPose, notes?: string | null): Observable<ProgressPhoto> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('takenOn', takenOn);
    fd.append('pose', pose);
    if (notes) fd.append('notes', notes);
    return this.http.post<ProgressPhoto>(`${this.base}/photos/me`, fd);
  }

  findMine(): Observable<ProgressPhoto[]> {
    return this.http.get<ProgressPhoto[]>(`${this.base}/photos/me`);
  }

  deleteMine(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/photos/me/${id}`);
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  findForClient(clientId: number): Observable<ProgressPhoto[]> {
    return this.http.get<ProgressPhoto[]>(`${this.base}/clients/${clientId}/photos`);
  }

  comment(id: number, comment: string): Observable<ProgressPhoto> {
    return this.http.patch<ProgressPhoto>(`${this.base}/photos/${id}/comment`, { comment });
  }

  // ── Shared ──────────────────────────────────────────────────────────────

  content(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/photos/${id}/content`, { responseType: 'blob' });
  }
}
