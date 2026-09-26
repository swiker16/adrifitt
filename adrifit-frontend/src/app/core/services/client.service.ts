import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Client, ClientCreatedResponse, CreateClientRequest, UpdateClientRequest } from '../../shared/models/client.model';

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/clients`;

  findAll(): Observable<Client[]> {
    return this.http.get<Client[]>(this.baseUrl);
  }

  /** Client role: own profile. */
  findMe(): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/me`);
  }

  resetPassword(id: number): Observable<{ temporaryPassword: string }> {
    return this.http.post<{ temporaryPassword: string }>(`${this.baseUrl}/${id}/reset-password`, {});
  }

  findById(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/${id}`);
  }

  create(payload: CreateClientRequest): Observable<ClientCreatedResponse> {
    return this.http.post<ClientCreatedResponse>(this.baseUrl, payload);
  }

  update(id: number, payload: UpdateClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  uploadPhoto(id: number, file: File): Observable<Client> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Client>(`${this.baseUrl}/${id}/photo`, formData);
  }
}
