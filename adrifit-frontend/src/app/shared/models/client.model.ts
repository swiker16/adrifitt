export interface Client {
  id: number;
  userId: number;
  username?: string;
  email?: string;
  firstName: string;
  lastName: string;
  phone?: string;
  birthDate?: string;
  objective?: string;
  notes?: string;
  trainerId?: number;
  photoBase64?: string;
  createdAt: string;
}

export interface CreateClientRequest {
  firstName: string;
  lastName: string;
  phone: string;
  birthDate: string;
  objective: string;
  email: string;
  planId: number;
  trainerId?: number;
  billingPeriod?: import('./plan.model').BillingPeriod;
  /** Special conditions: price per billing period instead of the plan price. */
  customPrice?: number | null;
  customPriceNote?: string | null;
  notes?: string;
}

export interface ClientCreatedResponse {
  client: Client;
  username: string;
  temporaryPassword: string;
}

export interface UpdateClientRequest {
  firstName: string;
  lastName: string;
  phone?: string;
  birthDate?: string;
  objective?: string;
  notes?: string;
}
