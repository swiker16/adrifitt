import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { ClientDashboard, TrainerDashboard } from '../../shared/models/dashboard.model';
import { BusinessOverview } from '../../shared/models/business.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly changes = new Subject<void>();

  /** Emits when a page changed something the menu badges count (e.g. a video was corrected). */
  readonly changed$ = this.changes.asObservable();

  notifyChanged(): void {
    this.changes.next();
  }

  getTrainerDashboard(): Observable<TrainerDashboard> {
    return this.http.get<TrainerDashboard>(`${API_BASE_URL}/dashboard/trainer`);
  }

  getBusinessOverview(): Observable<BusinessOverview> {
    return this.http.get<BusinessOverview>(`${API_BASE_URL}/dashboard/business`);
  }

  getClientDashboard(): Observable<ClientDashboard> {
    return this.http.get<ClientDashboard>(`${API_BASE_URL}/dashboard/client`);
  }
}
