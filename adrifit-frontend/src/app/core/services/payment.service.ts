import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BizumPaymentRequest,
  CardPaymentRequest,
  CreatePaymentRequest,
  Payment,
  PaymentStatus,
  PaymentSummary,
} from '../../shared/models/payment.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Trainer ─────────────────────────────────────────────────────────────

  findAll(filters: { status?: PaymentStatus | null; clientId?: number | null } = {}): Observable<Payment[]> {
    let params = new HttpParams();
    if (filters.status) params = params.set('status', filters.status);
    if (filters.clientId) params = params.set('clientId', filters.clientId);
    return this.http.get<Payment[]>(`${this.base}/payments`, { params });
  }

  summary(): Observable<PaymentSummary> {
    return this.http.get<PaymentSummary>(`${this.base}/payments/summary`);
  }

  create(request: CreatePaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments`, request);
  }

  markCashPaid(id: number, notes?: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${id}/cash`, { notes: notes || null });
  }

  cancel(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${id}/cancel`, {});
  }

  refund(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${id}/refund`, {});
  }

  findForClient(clientId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.base}/clients/${clientId}/payments`);
  }

  // ── Client ──────────────────────────────────────────────────────────────

  findMine(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.base}/payments/me`);
  }

  payWithCard(id: number, request: CardPaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/me/${id}/card`, request);
  }

  payWithBizum(id: number, request: BizumPaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/me/${id}/bizum`, request);
  }
}
