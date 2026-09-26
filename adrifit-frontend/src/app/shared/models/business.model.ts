export interface MonthAmount {
  /** yyyy-MM */
  month: string;
  amount: number;
  payments: number;
}

export interface MethodAmount {
  method: 'CARD' | 'BIZUM' | 'CASH';
  amount: number;
  payments: number;
}

export interface PlanBreakdown {
  planId: number;
  planName: string;
  monthlyPrice: number;
  activeClients: number;
  mrr: number;
}

export interface MonthCount {
  month: string;
  count: number;
}

export interface BusinessOverview {
  totalClients: number;
  activeSubscriptions: number;
  pausedSubscriptions: number;
  cancellationsLast30Days: number;
  monthlyRecurringRevenue: number;
  revenueThisMonth: number;
  revenueLastMonth: number;
  revenueLast12Months: number;
  refundedLast12Months: number;
  pendingAmount: number;
  overdueAmount: number;
  averageRevenuePerActiveClient: number;
  churnRatePercent: number;
  revenueByMonth: MonthAmount[];
  revenueByMethod: MethodAmount[];
  plans: PlanBreakdown[];
  newClientsByMonth: MonthCount[];
}
