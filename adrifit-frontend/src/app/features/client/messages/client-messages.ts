import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { EMPTY, catchError, interval, switchMap } from 'rxjs';
import { MessageService } from '../../../core/services/message.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { ChatMessage, MyConversation } from '../../../shared/models/message.model';

interface DayGroup {
  key: string;
  date: Date;
  messages: ChatMessage[];
}

@Component({
  selector: 'app-client-messages',
  imports: [MatIconModule, DatePipe, FormsModule, RouterLink],
  templateUrl: './client-messages.html',
  styleUrl: './client-messages.scss',
})
export class ClientMessages {
  private readonly messageService = inject(MessageService);
  private readonly notify = inject(NotifyService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly body = viewChild<ElementRef<HTMLElement>>('chatBody');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly conversation = signal<MyConversation | null>(null);
  readonly draft = signal('');
  readonly sending = signal(false);

  readonly enabled = computed(() => this.conversation()?.enabled ?? false);

  readonly groups = computed<DayGroup[]>(() => {
    const msgs = this.conversation()?.messages ?? [];
    const out: DayGroup[] = [];
    for (const m of msgs) {
      const d = new Date(m.createdAt);
      const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      let g = out[out.length - 1];
      if (!g || g.key !== key) {
        g = { key, date: d, messages: [] };
        out.push(g);
      }
      g.messages.push(m);
    }
    return out;
  });

  constructor() {
    this.load(true);
    interval(10000)
      .pipe(
        switchMap(() => this.messageService.getMine().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (c) => this.apply(c),
        error: () => {},
      });
  }

  load(first = false): void {
    if (first) this.loading.set(true);
    this.error.set(null);
    this.messageService.getMine().subscribe({
      next: (c) => {
        this.loading.set(false);
        this.apply(c, true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo cargar la conversación.'));
      },
    });
  }

  private apply(c: MyConversation, forceRead = false): void {
    const prev = this.conversation();
    const prevCount = prev?.messages.length ?? 0;
    const prevLastId = prev?.messages[prevCount - 1]?.id;
    this.conversation.set(c);
    if (!c.enabled) return;

    const lastId = c.messages[c.messages.length - 1]?.id;
    const changed = lastId !== prevLastId;
    const unreadFromTrainer = c.messages.some((m) => m.senderRole === 'TRAINER' && !m.readAt);
    if (unreadFromTrainer && (forceRead || changed)) {
      this.messageService.markMineRead().subscribe({ error: () => {} });
    }
    if (changed || forceRead) this.scrollToBottom();
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.body()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const content = this.draft().trim();
    if (!content || this.sending()) return;
    this.sending.set(true);
    this.messageService.sendMine(content).subscribe({
      next: (msg) => {
        this.sending.set(false);
        this.draft.set('');
        this.conversation.update((c) => (c ? { ...c, messages: [...c.messages, msg] } : c));
        this.scrollToBottom();
      },
      error: (err) => {
        this.sending.set(false);
        this.notify.error(err, 'No se pudo enviar el mensaje.');
      },
    });
  }

  isToday(d: Date): boolean {
    const n = new Date();
    return d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate();
  }

  isYesterday(d: Date): boolean {
    const y = new Date();
    y.setDate(y.getDate() - 1);
    return d.getFullYear() === y.getFullYear() && d.getMonth() === y.getMonth() && d.getDate() === y.getDate();
  }
}
