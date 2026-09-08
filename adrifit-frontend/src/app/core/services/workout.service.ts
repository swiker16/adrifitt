import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AssignWorkoutRequest, ClientWorkout, Workout, WorkoutRequest } from '../../shared/models/workout.model';

@Injectable({ providedIn: 'root' })
export class WorkoutService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/workouts`;

  findAll(): Observable<Workout[]> {
    return this.http.get<Workout[]>(this.base);
  }

  findById(id: number): Observable<Workout> {
    return this.http.get<Workout>(`${this.base}/${id}`);
  }

  create(payload: WorkoutRequest): Observable<Workout> {
    return this.http.post<Workout>(this.base, payload);
  }

  update(id: number, payload: WorkoutRequest): Observable<Workout> {
    return this.http.put<Workout>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  duplicate(id: number): Observable<Workout> {
    return this.http.post<Workout>(`${this.base}/${id}/duplicate`, {});
  }

  assignToClient(clientId: number, payload: AssignWorkoutRequest): Observable<ClientWorkout> {
    return this.http.post<ClientWorkout>(`${this.base}/clients/${clientId}/assign`, payload);
  }

  getActiveForClient(clientId: number): Observable<ClientWorkout> {
    return this.http.get<ClientWorkout>(`${this.base}/clients/${clientId}/active`);
  }

  getHistoryForClient(clientId: number): Observable<ClientWorkout[]> {
    return this.http.get<ClientWorkout[]>(`${this.base}/clients/${clientId}/history`);
  }

  getMyWorkout(): Observable<ClientWorkout> {
    return this.http.get<ClientWorkout>(`${this.base}/me`);
  }

  downloadPdf(workoutId: number, clientId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${workoutId}/pdf/${clientId}`, {
      responseType: 'blob',
    });
  }
}
