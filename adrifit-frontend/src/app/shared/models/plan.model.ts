export type BillingPeriod = 'MONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'ANNUAL';

export const BILLING_PERIODS: BillingPeriod[] = ['MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'];

export const BILLING_PERIOD_LABEL: Record<BillingPeriod, string> = {
  MONTHLY: 'Mensual',
  QUARTERLY: 'Trimestral',
  SEMIANNUAL: 'Semestral',
  ANNUAL: 'Anual',
};

/** Short suffix for prices, e.g. "117 € /mes", "345 € /trimestre". */
export const BILLING_PERIOD_SUFFIX: Record<BillingPeriod, string> = {
  MONTHLY: '/mes',
  QUARTERLY: '/trimestre',
  SEMIANNUAL: '/semestre',
  ANNUAL: '/año',
};

export const BILLING_PERIOD_MONTHS: Record<BillingPeriod, number> = {
  MONTHLY: 1,
  QUARTERLY: 3,
  SEMIANNUAL: 6,
  ANNUAL: 12,
};

/** A billing option offered by a plan (only periods with a price are listed). */
export interface PlanPeriodPrice {
  period: BillingPeriod;
  months: number;
  price: number;
  monthlyEquivalent: number;
  /** Saving vs. paying monthly for the same months, in %. */
  savingPercent: number;
}

export interface Plan {
  id: number;
  name: string;
  description?: string;
  monthlyPrice: number;
  quarterlyPrice: number | null;
  semiannualPrice: number | null;
  annualPrice: number | null;
  prices: PlanPeriodPrice[];
  /** What the plan includes, one item per entry ("Título: descripción"). */
  features: string[];
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
  quarterlyPrice?: number | null;
  semiannualPrice?: number | null;
  annualPrice?: number | null;
  /** One feature per line. */
  features?: string | null;
  reviewFrequencyDays: number;
  messagingEnabled: boolean;
  analyticsEnabled: boolean;
  pdfExportEnabled: boolean;
  prioritySupport: boolean;
  active?: boolean;
}

/** Splits "Título: descripción" into its parts (description may be empty). */
export function splitFeature(feature: string): { title: string; detail: string } {
  const i = feature.indexOf(':');
  if (i <= 0 || i > 70) return { title: feature, detail: '' };
  return { title: feature.slice(0, i).trim(), detail: feature.slice(i + 1).trim() };
}
