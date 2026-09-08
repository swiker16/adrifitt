export interface Plan {
  id: number;
  name: string;
  description?: string;
  monthlyPrice: number;
  reviewFrequencyDays: number;
  messagingEnabled: boolean;
  analyticsEnabled: boolean;
  pdfExportEnabled: boolean;
  prioritySupport: boolean;
  active: boolean;
  createdAt: string;
}

export interface PlanRequest {
  name: string;
  description?: string;
  monthlyPrice: number;
  reviewFrequencyDays: number;
  messagingEnabled: boolean;
  analyticsEnabled: boolean;
  pdfExportEnabled: boolean;
  prioritySupport: boolean;
  active?: boolean;
}
