export type EmailType =
  | 'WELCOME'
  | 'PASSWORD_RESET'
  | 'PAYMENT_DUE'
  | 'PAYMENT_RECEIPT'
  | 'SUBSCRIPTION'
  | 'REPORT_FEEDBACK'
  | 'REVIEW_REMINDER'
  | 'CUSTOM';
export type EmailStatus = 'QUEUED' | 'TEST_CAPTURED' | 'SENT' | 'FAILED';

export interface EmailMessage {
  id: number;
  clientId: number | null;
  toAddress: string;
  toName: string | null;
  subject: string;
  htmlBody: string;
  type: EmailType;
  status: EmailStatus;
  mode: string;
  errorMessage: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface SendEmailRequest {
  clientIds: number[];
  allActiveClients: boolean;
  subject: string;
  body: string;
}

export const EMAIL_TYPE_LABEL: Record<EmailType, string> = {
  WELCOME: 'Bienvenida',
  PASSWORD_RESET: 'Contraseña',
  PAYMENT_DUE: 'Pago pendiente',
  PAYMENT_RECEIPT: 'Recibo',
  SUBSCRIPTION: 'Suscripción',
  REPORT_FEEDBACK: 'Feedback',
  REVIEW_REMINDER: 'Recordatorio',
  CUSTOM: 'Personalizado',
};

export const EMAIL_STATUS_LABEL: Record<EmailStatus, string> = {
  QUEUED: 'En cola',
  TEST_CAPTURED: 'Modo test',
  SENT: 'Enviado',
  FAILED: 'Error',
};
