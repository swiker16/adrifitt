import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AssignPlanRequest,
  Subscription,
  SubscriptionStatus,
  UpdatePricingRequest,
} from '../../shared/models/subscription.model';
import { BillingPeriod } from '../../shared/models/plan.model';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = API_BASE_URL;

  // ── Trainer ─────────────────────────────────────────────────────────────

  getActive(clientId: number): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription`);
  }

  getHistory(clientId: number): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.baseUrl}/clients/${clientId}/subscription/history`);
  }

  assignPlan(clientId: number, payload: AssignPlanRequest): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription`, payload);
  }

  updateStatus(clientId: number, status: SubscriptionStatus): Observable<Subscription> {
    return this.http.patch<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription/status`, { status });
  }

  /** Special conditions (custom price) of the client's current subscription. */
  updatePricing(clientId: number, payload: UpdatePricingRequest): Observable<Subscription> {
    return this.http.patch<Subscription>(`${this.baseUrl}/clients/${clientId}/subscription/pricing`, payload);
  }

  // ── Client ──────────────────────────────────────────────────────────────

  getMine(): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.baseUrl}/subscriptions/me`);
  }

  getMyHistory(): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.baseUrl}/subscriptions/me/history`);
  }

  changeMyPlan(planId: number, billingPeriod: BillingPeriod = 'MONTHLY'): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.baseUrl}/subscriptions/me/change-plan`, { planId, billingPeriod });
  }

  cancelMine(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.baseUrl}/subscriptions/me/cancel`, {});
  }

  resumeMine(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.baseUrl}/subscriptions/me/resume`, {});
  }
}
