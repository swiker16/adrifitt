import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AssignPlanRequest,
  Subscription,
} from '../../shared/models/subscription.model';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_BASE_URL;

  getActive(clientId: number): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription`);
  }

  getHistory(clientId: number): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.baseUrl}/clients/${clientId}/subscription/history`);
  }

  assignPlan(clientId: number, payload: AssignPlanRequest): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription`, payload);
  }

  getMine(): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.baseUrl}/subscriptions/me`);
  }
}
