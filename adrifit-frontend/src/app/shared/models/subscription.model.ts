import { Plan } from './plan.model';

export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';

export interface Subscription {
  id: number;
  clientId: number;
  planId: number;
  planName: string;
  startDate: string;
  endDate: string | null;
  renewalDate: string;
  status: SubscriptionStatus;
  active: boolean;
  cancelAtPeriodEnd: boolean;
  cancelledAt: string | null;
  plan: Plan;
}

export interface AssignPlanRequest {
  planId: number;
}

export const SUBSCRIPTION_STATUS_LABEL: Record<SubscriptionStatus, string> = {
  ACTIVE: 'Activa',
  PAUSED: 'Pausada',
  CANCELLED: 'Cancelada',
};
