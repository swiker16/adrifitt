import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  ChatMessage,
  Conversation,
  ConversationSummary,
  MyConversation,
} from '../../shared/models/message.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/messages`;

  // ── Client ──────────────────────────────────────────────────────────────

  getMine(): Observable<MyConversation> {
    return this.http.get<MyConversation>(`${this.base}/me`);
  }

  sendMine(content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.base}/me`, { content });
  }

  markMineRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/me/read`, {});
  }

  myUnreadCount(): Observable<number> {
    return this.http.get<{ unread: number }>(`${this.base}/me/unread-count`).pipe(map((r) => r.unread));
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  conversations(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`${this.base}/conversations`);
  }

  conversation(clientId: number): Observable<Conversation> {
    return this.http.get<Conversation>(`${this.base}/clients/${clientId}`);
  }

  sendToClient(clientId: number, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.base}/clients/${clientId}`, { content });
  }

  markClientRead(clientId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/clients/${clientId}/read`, {});
  }

  trainerUnreadCount(): Observable<number> {
    return this.http.get<{ unread: number }>(`${this.base}/unread-count`).pipe(map((r) => r.unread));
  }
}
