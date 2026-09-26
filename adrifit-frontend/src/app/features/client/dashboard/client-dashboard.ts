import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { ClientDashboard as ClientDashboardData } from '../../../shared/models/dashboard.model';
import { SUBSCRIPTION_STATUS_LABEL } from '../../../shared/models/subscription.model';
import { BILLING_PERIOD_SUFFIX } from '../../../shared/models/plan.model';
import { REPORT_MAX_PHOTOS, REPORT_MIN_PHOTOS } from '../../../shared/models/report.model';
import { ProgressPhoto } from '../../../shared/models/photo.model';
import { SecureImg } from '../../../shared/components/secure-img';
import { PhotoLightbox } from '../report/photo-lightbox';

@Component({
  selector: 'app-client-dashboard',
  imports: [RouterLink, DatePipe, DecimalPipe, MatIconModule, SecureImg, PhotoLightbox],
  templateUrl: './client-dashboard.html',
  styleUrl: './client-dashboard.scss',
})
export class ClientDashboard {
  private readonly auth = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);

  readonly statusLabel = SUBSCRIPTION_STATUS_LABEL;
  readonly periodSuffix = BILLING_PERIOD_SUFFIX;
  readonly minPhotos = REPORT_MIN_PHOTOS;
  readonly maxPhotos = REPORT_MAX_PHOTOS;

  readonly lbPhotos = signal<ProgressPhoto[]>([]);
  readonly lbIndex = signal<number | null>(null);
  readonly today = new Date();

  readonly data = signal<ClientDashboardData | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly firstName = computed(
    () => this.data()?.profile?.firstName || this.auth.user()?.username || ''
  );

  readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 14 ? 'Buenos días' : h < 21 ? 'Buenas tardes' : 'Buenas noches';
  });

  readonly weightDiff = computed(() => {
    const d = this.data();
    if (d?.startWeight == null || d?.currentWeight == null) return null;
    return Math.round((d.currentWeight - d.startWeight) * 10) / 10;
  });

  /** Days until the next review (negative = overdue). */
  readonly reviewInDays = computed(() => {
    const date = this.data()?.nextReviewDate;
    if (!date) return null;
    const target = new Date(date + (date.length === 10 ? 'T00:00:00' : ''));
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    target.setHours(0, 0, 0, 0);
    return Math.round((target.getTime() - today.getTime()) / 86400000);
  });

  constructor() {
    this.load();
  }

  openPhotos(photos: ProgressPhoto[], index: number): void {
    this.lbPhotos.set(photos);
    this.lbIndex.set(index);
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.getClientDashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'No se pudo cargar tu panel.'));
        this.loading.set(false);
      },
    });
  }
}
