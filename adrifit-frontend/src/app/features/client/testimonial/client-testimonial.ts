import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TestimonialService } from '../../../core/services/testimonial.service';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { Testimonial } from '../../../shared/models/testimonial.model';

@Component({
  selector: 'app-client-testimonial',
  imports: [DatePipe, FormsModule, MatIconModule],
  templateUrl: './client-testimonial.html',
  styleUrl: './client-testimonial.scss',
})
export class ClientTestimonial {
  private readonly testimonialService = inject(TestimonialService);
  private readonly notify = inject(NotifyService);

  readonly MIN = 10;
  readonly MAX = 1000;
  readonly stars = [1, 2, 3, 4, 5];
  readonly ratingLabels = ['', 'Mala', 'Regular', 'Buena', 'Muy buena', 'Excelente'];

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly mine = signal<Testimonial | null>(null);

  readonly rating = signal(0);
  readonly hover = signal(0);
  readonly content = signal('');
  readonly touched = signal(false);
  readonly confirming = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly length = computed(() => this.content().trim().length);
  readonly shownRating = computed(() => this.hover() || this.rating());
  readonly valid = computed(() => this.rating() >= 1 && this.length() >= this.MIN && this.length() <= this.MAX);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.testimonialService.findMine().subscribe({
      next: (t) => {
        this.mine.set(t);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err?.status === 404) {
          this.mine.set(null);
        } else {
          this.loadError.set(apiErrorMessage(err, 'No se pudo cargar tu reseña.'));
        }
      },
    });
  }

  setRating(n: number): void {
    this.rating.set(n);
  }

  onStarKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.rating.update((r) => Math.min(5, r + 1));
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.rating.update((r) => Math.max(1, r - 1));
    } else {
      return;
    }
    // Roving focus: move the focus to the selected star
    const group = (event.target as HTMLElement).closest('[role="radiogroup"]');
    const buttons = group?.querySelectorAll<HTMLButtonElement>('button.star');
    buttons?.[this.rating() - 1]?.focus();
  }

  askPublish(): void {
    this.touched.set(true);
    if (!this.valid()) return;
    this.saveError.set(null);
    this.confirming.set(true);
  }

  publish(): void {
    if (this.saving() || !this.valid()) return;
    this.saving.set(true);
    this.saveError.set(null);
    this.testimonialService.create({ rating: this.rating(), content: this.content().trim() }).subscribe({
      next: (t) => {
        this.saving.set(false);
        this.confirming.set(false);
        this.mine.set(t);
        this.notify.success('¡Gracias! Tu reseña se ha publicado.');
      },
      error: (err) => {
        this.saving.set(false);
        this.confirming.set(false);
        this.saveError.set(apiErrorMessage(err, 'No se pudo publicar la reseña.'));
        if (err?.status === 409) this.load();
      },
    });
  }
}
