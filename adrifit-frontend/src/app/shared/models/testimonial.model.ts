export interface PublicTestimonial {
  id: number;
  authorName: string;
  planName: string | null;
  rating: number;
  content: string;
  createdAt: string;
}

export interface Testimonial extends PublicTestimonial {
  clientId: number;
  visible: boolean;
}

export interface CreateTestimonialRequest {
  rating: number;
  content: string;
}
