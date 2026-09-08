import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DietService } from '../../../core/services/diet.service';
import { ClientDiet, DietDay } from '../../../shared/models/diet.model';

@Component({
  selector: 'app-client-diet',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './client-diet.html',
  styleUrl: './client-diet.scss',
})
export class ClientDietView {
  private readonly dietService = inject(DietService);

  readonly clientDiet = signal<ClientDiet | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly activeDayIndex = signal(0);
  readonly downloading = signal(false);

  constructor() {
    this.dietService.getMyDiet().subscribe({
      next: (cd) => { this.clientDiet.set(cd); this.loading.set(false); },
      error: (err) => {
        this.error.set(err.status === 404 ? 'no-diet' : 'error');
        this.loading.set(false);
      },
    });
  }

  activeDay(): DietDay | null {
    const days = this.clientDiet()?.diet?.days;
    if (!days?.length) return null;
    return days[this.activeDayIndex()] ?? null;
  }

  downloadPdf(): void {
    this.downloading.set(true);
    const url = this.dietService.getMyDietPdfUrl();
    fetch(url, { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } })
      .then(r => r.blob())
      .then(blob => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = 'mi-dieta.pdf';
        link.click();
        URL.revokeObjectURL(link.href);
        this.downloading.set(false);
      })
      .catch(() => this.downloading.set(false));
  }
}
