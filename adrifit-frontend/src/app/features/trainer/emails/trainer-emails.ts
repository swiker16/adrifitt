import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatIconModule } from '@angular/material/icon';
import { EmailService } from '../../../core/services/email.service';
import { ClientService } from '../../../core/services/client.service';
import { NotifyService } from '../../../core/services/notify.service';
import { Client } from '../../../shared/models/client.model';
import {
  EMAIL_STATUS_LABEL,
  EMAIL_TYPE_LABEL,
  EmailMessage,
  EmailStatus,
  EmailType,
} from '../../../shared/models/email.model';

@Component({
  selector: 'app-trainer-emails',
  imports: [MatIconModule, FormsModule, DatePipe],
  templateUrl: './trainer-emails.html',
  styleUrl: './trainer-emails.scss',
})
export class TrainerEmails {
  private readonly emailService = inject(EmailService);
  private readonly clientService = inject(ClientService);
  private readonly notify = inject(NotifyService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly typeLabel = EMAIL_TYPE_LABEL;
  readonly statusLabel = EMAIL_STATUS_LABEL;
  readonly typeOptions = Object.entries(EMAIL_TYPE_LABEL) as [EmailType, string][];

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly emails = signal<EmailMessage[]>([]);
  readonly clients = signal<Client[]>([]);

  readonly clientFilter = signal<number | null>(null);
  readonly typeFilter = signal<EmailType | ''>('');

  readonly sortedClients = computed(() =>
    [...this.clients()].sort((a, b) => this.fullName(a).localeCompare(this.fullName(b), 'es')),
  );

  readonly filtered = computed(() => {
    const type = this.typeFilter();
    return this.emails()
      .filter((e) => !type || e.type === type)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  });

  // Preview
  readonly preview = signal<EmailMessage | null>(null);
  readonly previewHtml = computed<SafeHtml | null>(() => {
    const e = this.preview();
    // Only ever rendered inside <iframe sandbox=""> (no scripts, no same-origin).
    return e ? this.sanitizer.bypassSecurityTrustHtml(e.htmlBody ?? '') : null;
  });

  // Compose
  readonly showCompose = signal(false);
  readonly sending = signal(false);
  readonly composeError = signal<string | null>(null);
  readonly allActive = signal(false);
  readonly selectedIds = signal<Set<number>>(new Set());
  readonly search = signal('');
  subject = '';
  body = '';

  readonly searchedClients = computed(() => {
    const q = this.normalize(this.search());
    const list = this.sortedClients();
    if (!q) return list;
    return list.filter((c) => this.normalize(`${this.fullName(c)} ${c.email ?? ''}`).includes(q));
  });

  constructor() {
    this.clientService.findAll().subscribe({
      next: (c) => this.clients.set(c),
      error: () => this.clients.set([]),
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.emailService.findAll(this.clientFilter()).subscribe({
      next: (e) => {
        this.emails.set(e);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  setClient(value: number | string | null): void {
    const id = Number(value);
    this.clientFilter.set(id > 0 ? id : null);
    this.load();
  }

  fullName(c: Client): string {
    return `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim();
  }

  private normalize(s: string): string {
    return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
  }

  statusClass(s: EmailStatus): string {
    return { QUEUED: 'blue', TEST_CAPTURED: 'violet', SENT: 'green', FAILED: 'red' }[s];
  }

  typeIcon(t: EmailType): string {
    const icons: Record<EmailType, string> = {
      WELCOME: 'waving_hand',
      PASSWORD_RESET: 'key',
      PAYMENT_DUE: 'request_quote',
      PAYMENT_RECEIPT: 'receipt_long',
      SUBSCRIPTION: 'card_membership',
      REPORT_FEEDBACK: 'rate_review',
      REVIEW_REMINDER: 'event',
      CUSTOM: 'edit_note',
      LEAD_RECEIVED: 'mark_email_read',
      LEAD_NOTIFICATION: 'person_add',
      QUESTIONNAIRE_INVITE: 'assignment',
      LEAD_REJECTED: 'block',
      ACCOUNT_ACTIVATION: 'how_to_reg',
    };
    return icons[t];
  }

  // ── Compose ───────────────────────────────────────────────────────────
  openCompose(): void {
    this.subject = '';
    this.body = '';
    this.search.set('');
    this.allActive.set(false);
    this.selectedIds.set(new Set());
    this.composeError.set(null);
    this.showCompose.set(true);
  }

  isSelected(id: number): boolean {
    return this.selectedIds().has(id);
  }

  toggleClient(id: number): void {
    this.selectedIds.update((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  selectVisible(): void {
    this.selectedIds.update((s) => {
      const next = new Set(s);
      this.searchedClients().forEach((c) => next.add(c.id));
      return next;
    });
  }

  clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  send(): void {
    const subject = this.subject.trim();
    const body = this.body.trim();
    const ids = [...this.selectedIds()];
    if (!this.allActive() && ids.length === 0) return this.composeError.set('Elige al menos un destinatario.');
    if (!subject) return this.composeError.set('El asunto es obligatorio.');
    if (!body) return this.composeError.set('Escribe el mensaje.');
    this.composeError.set(null);
    this.sending.set(true);
    this.emailService
      .send({ clientIds: this.allActive() ? [] : ids, allActiveClients: this.allActive(), subject, body })
      .subscribe({
        next: (res) => {
          this.sending.set(false);
          this.showCompose.set(false);
          this.notify.success(`${res.length} ${res.length === 1 ? 'email generado' : 'emails generados'}`);
          this.load();
        },
        error: (err) => {
          this.sending.set(false);
          this.notify.error(err, 'No se pudieron generar los emails.');
        },
      });
  }
}
