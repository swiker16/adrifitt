import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DailyJobResult,
  ReviewScheduleItem,
  SaveTaskRequest,
  TaskStatus,
  TrainerTask,
} from '../../shared/models/task.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  findAll(filters: { status?: TaskStatus | null; clientId?: number | null } = {}): Observable<TrainerTask[]> {
    let params = new HttpParams();
    if (filters.status) params = params.set('status', filters.status);
    if (filters.clientId) params = params.set('clientId', filters.clientId);
    return this.http.get<TrainerTask[]>(`${this.base}/tasks`, { params });
  }

  create(request: SaveTaskRequest): Observable<TrainerTask> {
    return this.http.post<TrainerTask>(`${this.base}/tasks`, request);
  }

  update(id: number, request: SaveTaskRequest): Observable<TrainerTask> {
    return this.http.put<TrainerTask>(`${this.base}/tasks/${id}`, request);
  }

  complete(id: number): Observable<TrainerTask> {
    return this.http.patch<TrainerTask>(`${this.base}/tasks/${id}/complete`, {});
  }

  reopen(id: number): Observable<TrainerTask> {
    return this.http.patch<TrainerTask>(`${this.base}/tasks/${id}/reopen`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/tasks/${id}`);
  }

  reviewSchedule(): Observable<ReviewScheduleItem[]> {
    return this.http.get<ReviewScheduleItem[]>(`${this.base}/reviews/schedule`);
  }

  /** Runs renewals + review reminders now (normally a daily cron). */
  runDailyJobs(): Observable<DailyJobResult> {
    return this.http.post<DailyJobResult>(`${this.base}/jobs/daily/run`, {});
  }
}
