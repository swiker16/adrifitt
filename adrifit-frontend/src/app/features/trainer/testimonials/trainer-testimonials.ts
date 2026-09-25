import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TestimonialService } from '../../../core/services/testimonial.service';
import { NotifyService } from '../../../core/services/notify.service';
import { Testimonial } from '../../../shared/models/testimonial.model';

type VisibilityFilter = 'all' | 'visible' | 'hidden';

@Component({
  selector: 'app-trainer-testimonials',
  imports: [MatIconModule, DatePipe, DecimalPipe],
  templateUrl: './trainer-testimonials.html',
  styleUrl: './trainer-testimonials.scss',
})
export class TrainerTestimonials {
  private readonly testimonialService = inject(TestimonialService);
  private readonly notify = inject(NotifyService);

  readonly stars = [1, 2, 3, 4, 5];

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly testimonials = signal<Testimonial[]>([]);
  readonly filter = signal<VisibilityFilter>('all');
  readonly busyId = signal<number | null>(null);

  readonly sorted = computed(() =>
    [...this.testimonials()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  );

  readonly filtered = computed(() => {
    const f = this.filter();
    return this.sorted().filter((t) => f === 'all' || (f === 'visible' ? t.visible : !t.visible));
  });

  readonly average = computed(() => {
    const list = this.testimonials();
    return list.length ? list.reduce((acc, t) => acc + t.rating, 0) / list.length : 0;
  });

  readonly visibleCount = computed(() => this.testimonials().filter((t) => t.visible).length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.testimonialService.findAll().subscribe({
      next: (t) => {
        this.testimonials.set(t);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  initials(name: string): string {
    return (name ?? '?')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0])
      .join('')
      .toUpperCase();
  }

  toggle(t: Testimonial): void {
    const visible = !t.visible;
    this.busyId.set(t.id);
    this.testimonialService.setVisibility(t.id, visible).subscribe({
      next: (updated) => {
        this.testimonials.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.busyId.set(null);
        this.notify.success(visible ? 'Reseña visible en la web' : 'Reseña ocultada');
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo cambiar la visibilidad.');
      },
    });
  }
}
