import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ClientAnalysis, ReviewAnalysisRequest } from '../../shared/models/analysis.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Client ──────────────────────────────────────────────────────────────

  upload(title: string, analysisDate: string, clientComment: string | null, file: File): Observable<ClientAnalysis> {
    const fd = new FormData();
    fd.append('title', title);
    fd.append('analysisDate', analysisDate);
    if (clientComment) fd.append('clientComment', clientComment);
    fd.append('file', file);
    return this.http.post<ClientAnalysis>(`${this.base}/client/analyses`, fd);
  }

  getMyAnalyses(): Observable<ClientAnalysis[]> {
    return this.http.get<ClientAnalysis[]>(`${this.base}/client/analyses`);
  }

  getMyAnalysis(id: number): Observable<ClientAnalysis> {
    return this.http.get<ClientAnalysis>(`${this.base}/client/analyses/${id}`);
  }

  deleteMyAnalysis(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/client/analyses/${id}`);
  }

  downloadContent(id: number, disposition: 'inline' | 'attachment', role: 'client' | 'trainer'): Observable<Blob> {
    const url = role === 'client'
      ? `${this.base}/client/analyses/${id}/content?disposition=${disposition}`
      : `${this.base}/analyses/${id}/content?disposition=${disposition}`;
    return this.http.get(url, { responseType: 'blob' });
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  findAll(status?: 'UPLOADED' | 'REVIEWED' | null): Observable<ClientAnalysis[]> {
    const q = status ? `?status=${status}` : '';
    return this.http.get<ClientAnalysis[]>(`${this.base}/analyses${q}`);
  }

  getForClient(clientId: number): Observable<ClientAnalysis[]> {
    return this.http.get<ClientAnalysis[]>(`${this.base}/clients/${clientId}/analyses`);
  }

  getById(id: number): Observable<ClientAnalysis> {
    return this.http.get<ClientAnalysis>(`${this.base}/analyses/${id}`);
  }

  review(id: number, request: ReviewAnalysisRequest): Observable<ClientAnalysis> {
    return this.http.patch<ClientAnalysis>(`${this.base}/analyses/${id}/review`, request);
  }

  getPendingCount(): Observable<number> {
    return this.http.get<number>(`${this.base}/analyses/pending-count`);
  }
}
