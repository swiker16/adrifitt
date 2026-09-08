import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { TrainerDashboard } from '../../shared/models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  getTrainerDashboard(): Observable<TrainerDashboard> {
    return this.http.get<TrainerDashboard>(`${API_BASE_URL}/dashboard/trainer`);
  }
}
