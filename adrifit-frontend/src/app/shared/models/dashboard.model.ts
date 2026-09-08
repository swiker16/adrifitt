export interface PlanDistribution {
  planId: number;
  planName: string;
  clients: number;
}

export interface PendingReview {
  clientId: number;
  clientFirstName: string;
  clientLastName: string;
  planName: string;
  renewalDate: string;
  nextReviewDate: string;
  daysSinceLastReview: number;
}

export interface ReportWithoutFeedback {
  reportId: number;
  clientId: number;
  clientFirstName: string;
  clientLastName: string;
  reportDate: string;
  weight: number;
}

export interface ClientWithoutWorkout {
  clientId: number;
  clientFirstName: string;
  clientLastName: string;
  planName: string;
  lastWorkoutName?: string;
}

export interface UpcomingRenewal {
  clientId: number;
  clientFirstName: string;
  clientLastName: string;
  planName: string;
  amount: number;
  renewalDate: string;
  daysUntilRenewal: number;
}

export interface ActivityItem {
  type: string;
  clientId: number;
  clientFirstName: string;
  clientLastName: string;
  description: string;
  occurredAt: string;
}

export interface TrainerDashboard {
  totalClients: number;
  activeClients: number;
  totalReports: number;
  reviewsPending: number;
  reviewsThisWeek: number;
  reportsThisWeek: number;
  reportsPendingFeedback: number;
  newClientsThisMonth: number;
  clientsWithoutWorkout: number;
  upcomingRenewalsCount: number;
  clientsPerPlan: PlanDistribution[];
  pendingReviews: PendingReview[];
  reportsWithoutFeedback: ReportWithoutFeedback[];
  clientsWithoutWorkouts: ClientWithoutWorkout[];
  upcomingRenewals: UpcomingRenewal[];
  recentActivity: ActivityItem[];
  pendingAnalyses?: number;
}
