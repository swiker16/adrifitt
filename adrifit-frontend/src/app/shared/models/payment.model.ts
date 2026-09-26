export type PaymentStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'CANCELLED';
export type PaymentMethod = 'CARD' | 'BIZUM' | 'CASH';

export interface Payment {
  id: number;
  clientId: number;
  clientName: string | null;
  subscriptionId: number | null;
  concept: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  method: PaymentMethod | null;
  dueDate: string;
  periodStart: string | null;
  periodEnd: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  providerReference: string | null;
  cardBrand: string | null;
  cardLast4: string | null;
  bizumPhone: string | null;
  failedAttempts: number;
  lastFailureReason: string | null;
  trainerNotes: string | null;
  overdue: boolean;
  createdAt: string;
}

export interface PaymentSummary {
  pendingCount: number;
  pendingAmount: number;
  overdueCount: number;
  overdueAmount: number;
  paidThisMonthCount: number;
  paidThisMonthAmount: number;
  gatewayMode: string;
}

export interface CreatePaymentRequest {
  clientId: number;
  amount: number;
  concept: string;
  dueDate?: string;
}

export interface CardPaymentRequest {
  cardNumber: string;
  expMonth: number;
  expYear: number;
  cvc: string;
  holderName: string;
}

export interface BizumPaymentRequest {
  phone: string;
}

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Pendiente',
  PAID: 'Pagado',
  REFUNDED: 'Devuelto',
  CANCELLED: 'Anulado',
};

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  CARD: 'Tarjeta',
  BIZUM: 'Bizum',
  CASH: 'Efectivo',
};
