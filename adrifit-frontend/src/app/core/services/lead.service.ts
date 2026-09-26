import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthResponse } from '../../shared/models/auth.model';
import {
  ActivationInfo,
  ApproveLeadRequest,
  ContactRequest,
  LeadCounts,
  LeadDetail,
  LeadStatus,
  LeadSummary,
  Questionnaire,
  QuestionnaireInfo,
} from '../../shared/models/lead.model';
import { API_BASE_URL } from '../config/api.config';

/** New client intake: public contact/questionnaire/activation + the trainer's "Solicitudes". */
@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Public ──────────────────────────────────────────────────────────────

  contact(request: ContactRequest): Observable<unknown> {
    return this.http.post(`${this.base}/public/leads`, request);
  }

  questionnaireInfo(token: string): Observable<QuestionnaireInfo> {
    return this.http.get<QuestionnaireInfo>(`${this.base}/public/questionnaire/${encodeURIComponent(token)}`);
  }

  submitQuestionnaire(token: string, answers: Questionnaire): Observable<void> {
    return this.http.post<void>(`${this.base}/public/questionnaire/${encodeURIComponent(token)}`, answers);
  }

  activationInfo(token: string): Observable<ActivationInfo> {
    return this.http.get<ActivationInfo>(`${this.base}/public/activation/${encodeURIComponent(token)}`);
  }

  activate(token: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/public/activation/${encodeURIComponent(token)}`, { password });
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  findAll(status?: LeadStatus | null): Observable<LeadSummary[]> {
    return this.http.get<LeadSummary[]>(`${this.base}/leads`, status ? { params: { status } } : {});
  }

  counts(): Observable<LeadCounts> {
    return this.http.get<LeadCounts>(`${this.base}/leads/counts`);
  }

  findById(id: number): Observable<LeadDetail> {
    return this.http.get<LeadDetail>(`${this.base}/leads/${id}`);
  }

  sendQuestionnaire(id: number, message?: string | null): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`${this.base}/leads/${id}/questionnaire`, { message: message || null });
  }

  reject(id: number, message?: string | null): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`${this.base}/leads/${id}/reject`, { message: message || null });
  }

  approve(id: number, request: ApproveLeadRequest): Observable<LeadDetail> {
    return this.http.post<LeadDetail>(`${this.base}/leads/${id}/approve`, request);
  }

  resendActivation(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/leads/${id}/resend-activation`, {});
  }

  updateNote(id: number, note: string | null): Observable<LeadDetail> {
    return this.http.patch<LeadDetail>(`${this.base}/leads/${id}/note`, { note });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/leads/${id}`);
  }

  /** Initial questionnaire of a client (null if they didn't come through a request). */
  questionnaireOfClient(clientId: number): Observable<Questionnaire | null> {
    return this.http.get<Questionnaire | null>(`${this.base}/clients/${clientId}/questionnaire`);
  }
}
