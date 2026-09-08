import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Plan, PlanRequest } from '../../shared/models/plan.model';

@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/plans`;

  findAll(activeOnly = false): Observable<Plan[]> {
    const params = new HttpParams().set('activeOnly', activeOnly);
    return this.http.get<Plan[]>(this.baseUrl, { params });
  }

  findById(id: number): Observable<Plan> {
    return this.http.get<Plan>(`${this.baseUrl}/${id}`);
  }

  create(payload: PlanRequest): Observable<Plan> {
    return this.http.post<Plan>(this.baseUrl, payload);
  }

  update(id: number, payload: PlanRequest): Observable<Plan> {
    return this.http.put<Plan>(`${this.baseUrl}/${id}`, payload);
  }

  deactivate(id: number): Observable<Plan> {
    return this.http.put<Plan>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  deletePermanently(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  activate(id: number): Observable<Plan> {
    return this.http.put<Plan>(`${this.baseUrl}/${id}/activate`, {});
  }
}
