import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DietService } from '../../../core/services/diet.service';
import { DietSummary } from '../../../shared/models/diet.model';

@Component({
  selector: 'app-diet-list',
  imports: [RouterLink, DatePipe, DecimalPipe, MatIconModule],
  templateUrl: './diet-list.html',
  styleUrl: './diet-list.scss',
})
export class DietList {
  private readonly dietService = inject(DietService);

  readonly diets = signal<DietSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly search = signal('');
  readonly filterObjective = signal('');
  readonly filterStatus = signal<'all' | 'active' | 'inactive'>('all');

  readonly objectives = computed(() =>
    [...new Set(this.diets().map(d => d.objective).filter(Boolean) as string[])].sort()
  );

  readonly filtered = computed(() => {
    const q = this.search().toLowerCase();
    const obj = this.filterObjective();
    const st = this.filterStatus();
    return this.diets().filter(d => {
      if (q && !d.name.toLowerCase().includes(q)) return false;
      if (obj && d.objective !== obj) return false;
      if (st === 'active' && !d.active) return false;
      if (st === 'inactive' && d.active) return false;
      return true;
    });
  });

  constructor() { this.load(); }

  private load(): void {
    this.loading.set(true);
    this.dietService.findAll().subscribe({
      next: (d) => { this.diets.set(d); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  duplicate(id: number): void {
    this.dietService.duplicate(id).subscribe({
      next: (d) => this.diets.update(list => [
        { id: d.id, name: d.name, description: d.description, objective: d.objective,
          active: d.active, dayCount: d.dayCount ?? 0, avgCalories: d.totalCalories,
          createdAt: d.createdAt, updatedAt: d.updatedAt },
        ...list,
      ]),
    });
  }

  toggleStatus(d: DietSummary): void {
    this.dietService.toggleStatus(d.id).subscribe({
      next: (updated) => this.diets.update(list =>
        list.map(x => x.id === d.id ? { ...x, active: updated.active } : x)
      ),
    });
  }

  delete(id: number): void {
    if (!confirm('¿Eliminar esta dieta? Solo se puede si no está activamente asignada.')) return;
    this.deletingId.set(id);
    this.dietService.delete(id).subscribe({
      next: () => { this.diets.update(list => list.filter(d => d.id !== id)); this.deletingId.set(null); },
      error: (err) => {
        alert(err?.error?.message ?? 'No se pudo eliminar. Puede estar asignada a un cliente.');
        this.deletingId.set(null);
      },
    });
  }
}
