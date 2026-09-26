import { BillingPeriod, Plan } from './plan.model';

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
  billingPeriod: BillingPeriod;
  /** Special conditions: price per billing period agreed with this client (null = plan price). */
  customPrice: number | null;
  customPriceNote: string | null;
  /** What is actually charged every billing period. */
  effectivePrice: number;
  monthlyEquivalent: number;
  cancelledAt: string | null;
  plan: Plan;
}

export interface AssignPlanRequest {
  planId: number;
  billingPeriod?: BillingPeriod;
  customPrice?: number | null;
  customPriceNote?: string | null;
}

export interface UpdatePricingRequest {
  /** null removes the special conditions. */
  customPrice: number | null;
  customPriceNote?: string | null;
}

export const SUBSCRIPTION_STATUS_LABEL: Record<SubscriptionStatus, string> = {
  ACTIVE: 'Activa',
  PAUSED: 'Pausada',
  CANCELLED: 'Cancelada',
};
