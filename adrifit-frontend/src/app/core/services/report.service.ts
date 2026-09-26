import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CoachFeedbackRequest, CreateWeeklyReportRequest, WeeklyReport } from '../../shared/models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_BASE_URL;

  /** Client check-in: 4-6 photos, weight and an optional comment. */
  submitMine(weight: number, comments: string | null, photos: File[]): Observable<WeeklyReport> {
    const fd = new FormData();
    fd.append('weight', String(weight));
    if (comments && comments.trim()) fd.append('comments', comments.trim());
    photos.forEach((f) => fd.append('files', f, f.name));
    return this.http.post<WeeklyReport>(`${this.baseUrl}/reports`, fd);
  }

  /** Client edits a check-in that has not been reviewed yet (weight / comment only). */
  updateMine(id: number, weight: number, comments: string | null): Observable<WeeklyReport> {
    return this.http.put<WeeklyReport>(`${this.baseUrl}/reports/${id}`, { weight, comments });
  }

  findMine(): Observable<WeeklyReport[]> {
    return this.http.get<WeeklyReport[]>(`${this.baseUrl}/reports/mine`);
  }

  findAll(): Observable<WeeklyReport[]> {
    return this.http.get<WeeklyReport[]>(`${this.baseUrl}/reports`);
  }

  findPending(): Observable<WeeklyReport[]> {
    return this.http.get<WeeklyReport[]>(`${this.baseUrl}/reports/pending`);
  }

  findByClient(clientId: number): Observable<WeeklyReport[]> {
    return this.http.get<WeeklyReport[]>(`${this.baseUrl}/clients/${clientId}/reports`);
  }

  create(clientId: number, payload: CreateWeeklyReportRequest): Observable<WeeklyReport> {
    return this.http.post<WeeklyReport>(`${this.baseUrl}/clients/${clientId}/reports`, payload);
  }

  findById(id: number): Observable<WeeklyReport> {
    return this.http.get<WeeklyReport>(`${this.baseUrl}/reports/${id}`);
  }

  setFeedback(id: number, payload: CoachFeedbackRequest): Observable<WeeklyReport> {
    return this.http.patch<WeeklyReport>(`${this.baseUrl}/reports/${id}/feedback`, payload);
  }
}
