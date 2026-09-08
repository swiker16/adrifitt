import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CoachFeedbackRequest, CreateWeeklyReportRequest, WeeklyReport } from '../../shared/models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_BASE_URL;

  createMine(payload: CreateWeeklyReportRequest): Observable<WeeklyReport> {
    return this.http.post<WeeklyReport>(`${this.baseUrl}/reports`, payload);
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
