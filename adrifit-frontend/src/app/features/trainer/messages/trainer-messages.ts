import { Component, DestroyRef, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { MessageService } from '../../../core/services/message.service';
import { NotifyService } from '../../../core/services/notify.service';
import { ChatMessage, Conversation, ConversationSummary } from '../../../shared/models/message.model';

interface DayGroup {
  day: string;
  messages: ChatMessage[];
}

@Component({
  selector: 'app-trainer-messages',
  imports: [DatePipe, FormsModule, MatIconModule, RouterLink],
  templateUrl: './trainer-messages.html',
  styleUrl: './trainer-messages.scss',
})
export class TrainerMessages {
  private readonly messageService = inject(MessageService);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly conversations = signal<ConversationSummary[]>([]);
  readonly listLoading = signal(true);
  readonly listError = signal(false);
  readonly search = signal('');

  readonly selectedId = signal<number | null>(null);
  readonly conversation = signal<Conversation | null>(null);
  readonly chatLoading = signal(false);
  readonly chatError = signal(false);

  readonly draft = signal('');
  readonly sending = signal(false);

  private lastMessageCount = 0;

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.conversations();
    return q ? list.filter((c) => c.clientName.toLowerCase().includes(q)) : list;
  });

  readonly selectedSummary = computed(() => this.conversations().find((c) => c.clientId === this.selectedId()) ?? null);

  readonly totalUnread = computed(() => this.conversations().reduce((s, c) => s + c.unreadCount, 0));

  readonly groups = computed<DayGroup[]>(() => {
    const msgs = this.conversation()?.messages ?? [];
    const groups: DayGroup[] = [];
    for (const m of msgs) {
      const day = m.createdAt.substring(0, 10);
      const last = groups[groups.length - 1];
      if (last && last.day === day) last.messages.push(m);
      else groups.push({ day, messages: [m] });
    }
    return groups;
  });

  constructor() {
    this.loadConversations();

    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = Number(params.get('clientId'));
      if (id && id !== this.selectedId()) this.open(id, false);
    });

    interval(10000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.loadConversations(true);
        const id = this.selectedId();
        if (id) this.loadConversation(id, true);
      });

    // Auto-scroll to the bottom when new messages arrive.
    effect(() => {
      const count = this.conversation()?.messages.length ?? 0;
      const el = this.scroller()?.nativeElement;
      if (!el) return;
      if (count !== this.lastMessageCount) {
        this.lastMessageCount = count;
        setTimeout(() => (el.scrollTop = el.scrollHeight));
      }
    });
  }

  loadConversations(silent = false): void {
    if (!silent) this.listLoading.set(true);
    this.messageService
      .conversations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.conversations.set(list);
          this.listLoading.set(false);
          this.listError.set(false);
        },
        error: () => {
          this.listLoading.set(false);
          if (!silent) this.listError.set(true);
        },
      });
  }

  open(clientId: number, updateUrl = true): void {
    this.selectedId.set(clientId);
    this.conversation.set(null);
    this.lastMessageCount = 0;
    this.draft.set('');
    this.loadConversation(clientId, false);
    if (updateUrl) {
      this.router.navigate([], { relativeTo: this.route, queryParams: { clientId }, replaceUrl: true });
    }
  }

  back(): void {
    this.selectedId.set(null);
    this.conversation.set(null);
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  private loadConversation(clientId: number, silent: boolean): void {
    if (!silent) {
      this.chatLoading.set(true);
      this.chatError.set(false);
    }
    this.messageService
      .conversation(clientId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (conv) => {
          if (this.selectedId() !== clientId) return;
          const hasUnread = conv.messages.some((m) => m.senderRole === 'CLIENT' && !m.readAt);
          this.conversation.set(conv);
          this.chatLoading.set(false);
          if (hasUnread) this.markRead(clientId);
        },
        error: (err) => {
          this.chatLoading.set(false);
          if (!silent) {
            this.chatError.set(true);
            this.notify.error(err, 'No se pudo cargar la conversación.');
          }
        },
      });
  }

  private markRead(clientId: number): void {
    this.messageService.markClientRead(clientId).subscribe({
      next: () =>
        this.conversations.update((list) => list.map((c) => (c.clientId === clientId ? { ...c, unreadCount: 0 } : c))),
      error: () => {},
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const clientId = this.selectedId();
    const content = this.draft().trim();
    const conv = this.conversation();
    if (!clientId || !content || this.sending() || !conv?.messagingEnabled) return;
    this.sending.set(true);
    this.messageService.sendToClient(clientId, content).subscribe({
      next: (msg) => {
        this.sending.set(false);
        this.draft.set('');
        this.conversation.update((c) => (c ? { ...c, messages: [...c.messages, msg] } : c));
        this.conversations.update((list) =>
          list.map((c) =>
            c.clientId === clientId
              ? { ...c, lastMessage: msg.content, lastMessageFrom: 'TRAINER', lastMessageAt: msg.createdAt }
              : c,
          ),
        );
      },
      error: (err) => {
        this.sending.set(false);
        this.notify.error(err, 'No se pudo enviar el mensaje.');
      },
    });
  }

  initials(name: string): string {
    return name
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toUpperCase();
  }

  isToday(iso: string | null): boolean {
    if (!iso) return false;
    return new Date(iso).toDateString() === new Date().toDateString();
  }

  dayLabel(day: string): string {
    const d = new Date(day + 'T00:00:00');
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (d.toDateString() === today.toDateString()) return 'Hoy';
    if (d.toDateString() === yesterday.toDateString()) return 'Ayer';
    return d.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
  }
}
