export type ReportStatus = 'PENDING' | 'REVIEWED';

export interface WeeklyReport {
  id: number;
  clientId: number;
  clientFirstName?: string;
  clientLastName?: string;
  weight: number;
  waist?: number;
  bodyFat?: number;
  energyLevel?: number;
  dietAdherence?: number;
  trainingAdherence?: number;
  comments?: string;
  coachFeedback?: string;
  status: ReportStatus;
  reviewedAt?: string;
  createdAt: string;
  updatedAt?: string;
  /** 4-6 photos sent with the check-in. */
  photos: import('./photo.model').ProgressPhoto[];
}

export const REPORT_MIN_PHOTOS = 4;
export const REPORT_MAX_PHOTOS = 6;

export interface CreateWeeklyReportRequest {
  weight: number;
  waist?: number;
  bodyFat?: number;
  energyLevel?: number;
  dietAdherence?: number;
  trainingAdherence?: number;
  comments?: string;
}

export interface CoachFeedbackRequest {
  coachFeedback: string;
}
