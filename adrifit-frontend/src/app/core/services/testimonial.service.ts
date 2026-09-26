import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateTestimonialRequest,
  PublicTestimonial,
  Testimonial,
} from '../../shared/models/testimonial.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class TestimonialService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  /** Public (no auth): reviews shown on the landing page. */
  findPublic(): Observable<PublicTestimonial[]> {
    return this.http.get<PublicTestimonial[]>(`${this.base}/public/testimonials`);
  }

  findMine(): Observable<Testimonial> {
    return this.http.get<Testimonial>(`${this.base}/testimonials/me`);
  }

  create(request: CreateTestimonialRequest): Observable<Testimonial> {
    return this.http.post<Testimonial>(`${this.base}/testimonials/me`, request);
  }

  findAll(): Observable<Testimonial[]> {
    return this.http.get<Testimonial[]>(`${this.base}/testimonials`);
  }

  setVisibility(id: number, visible: boolean): Observable<Testimonial> {
    return this.http.patch<Testimonial>(`${this.base}/testimonials/${id}/visibility`, { visible });
  }
}
