import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AssignDietRequest, ClientDiet, CreateDietRequest, Diet, DietSummary } from '../../shared/models/diet.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class DietService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Trainer ────────────────────────────────────────────────────────────

  findAll(): Observable<DietSummary[]> {
    return this.http.get<DietSummary[]>(`${this.base}/trainer/diets`);
  }

  findById(id: number): Observable<Diet> {
    return this.http.get<Diet>(`${this.base}/trainer/diets/${id}`);
  }

  create(request: CreateDietRequest): Observable<Diet> {
    return this.http.post<Diet>(`${this.base}/trainer/diets`, request);
  }

  update(id: number, request: CreateDietRequest): Observable<Diet> {
    return this.http.put<Diet>(`${this.base}/trainer/diets/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/trainer/diets/${id}`);
  }

  duplicate(id: number): Observable<Diet> {
    return this.http.post<Diet>(`${this.base}/trainer/diets/${id}/duplicate`, {});
  }

  toggleStatus(id: number): Observable<Diet> {
    return this.http.patch<Diet>(`${this.base}/trainer/diets/${id}/status`, {});
  }

  assignToClient(clientId: number, request: AssignDietRequest): Observable<ClientDiet> {
    return this.http.post<ClientDiet>(`${this.base}/clients/${clientId}/diets/assign`, request);
  }

  getActiveForClient(clientId: number): Observable<ClientDiet> {
    return this.http.get<ClientDiet>(`${this.base}/clients/${clientId}/diets/active`);
  }

  getHistoryForClient(clientId: number): Observable<ClientDiet[]> {
    return this.http.get<ClientDiet[]>(`${this.base}/clients/${clientId}/diets`);
  }

  getClientPdfUrl(clientId: number): string {
    return `${this.base}/clients/${clientId}/diets/active/pdf`;
  }

  // ── Client ─────────────────────────────────────────────────────────────

  getMyDiet(): Observable<ClientDiet> {
    return this.http.get<ClientDiet>(`${this.base}/client/diet`);
  }

  getMyDietPdfUrl(): string {
    return `${this.base}/client/diet/pdf`;
  }
}
