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
}

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
