import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DietService } from '../../../core/services/diet.service';
import { ClientDiet, DietDay } from '../../../shared/models/diet.model';
import { NotifyService } from '../../../core/services/notify.service';
import { saveBlob } from '../../../shared/utils/download';

@Component({
  selector: 'app-client-diet',
  imports: [DecimalPipe, MatIconModule],
  templateUrl: './client-diet.html',
  styleUrl: './client-diet.scss',
})
export class ClientDietView {
  private readonly dietService = inject(DietService);
  private readonly notify = inject(NotifyService);

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
    this.dietService.downloadMyDietPdf().subscribe({
      next: (blob) => {
        saveBlob(blob, 'mi-dieta.pdf');
        this.downloading.set(false);
      },
      error: (err) => {
        this.downloading.set(false);
        this.notify.error(err, 'No se pudo descargar el PDF.');
      },
    });
  }
}
