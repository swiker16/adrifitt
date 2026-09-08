import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DietService } from '../../../core/services/diet.service';
import { Diet, DietDay } from '../../../shared/models/diet.model';

@Component({
  selector: 'app-diet-details',
  imports: [RouterLink, DatePipe, DecimalPipe, MatIconModule],
  templateUrl: './diet-details.html',
  styleUrl: './diet-details.scss',
})
export class DietDetails {
  private readonly dietService = inject(DietService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly diet = signal<Diet | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly activeDayIndex = signal(0);

  constructor() {
    const id = +(this.route.snapshot.paramMap.get('id') ?? 0);
    this.dietService.findById(id).subscribe({
      next: (d) => { this.diet.set(d); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  activeDay(): DietDay | null {
    const diet = this.diet();
    if (!diet?.days?.length) return null;
    return diet.days[this.activeDayIndex()] ?? null;
  }

  duplicate(): void {
    const id = this.diet()?.id;
    if (!id) return;
    this.dietService.duplicate(id).subscribe({
      next: (d) => this.router.navigate(['/trainer/diets', d.id]),
    });
  }

  toggleStatus(): void {
    const id = this.diet()?.id;
    if (!id) return;
    this.dietService.toggleStatus(id).subscribe({
      next: (d) => this.diet.update(old => old ? { ...old, active: d.active } : null),
    });
  }

  delete(): void {
    const id = this.diet()?.id;
    if (!id || !confirm('¿Eliminar esta dieta? Solo se puede si no está asignada activamente.')) return;
    this.dietService.delete(id).subscribe({
      next: () => this.router.navigate(['/trainer/diets']),
      error: (err) => alert(err?.error?.message ?? 'No se pudo eliminar.'),
    });
  }
}
