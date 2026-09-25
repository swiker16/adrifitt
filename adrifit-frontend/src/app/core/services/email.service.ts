import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { EmailMessage, SendEmailRequest } from '../../shared/models/email.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class EmailService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/emails`;

  findAll(clientId?: number | null): Observable<EmailMessage[]> {
    const params = clientId ? new HttpParams().set('clientId', clientId) : undefined;
    return this.http.get<EmailMessage[]>(this.base, { params });
  }

  findById(id: number): Observable<EmailMessage> {
    return this.http.get<EmailMessage>(`${this.base}/${id}`);
  }

  send(request: SendEmailRequest): Observable<EmailMessage[]> {
    return this.http.post<EmailMessage[]>(this.base, request);
  }
}
