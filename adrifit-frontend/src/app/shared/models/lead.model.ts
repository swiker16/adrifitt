import { BillingPeriod } from './plan.model';

export type LeadStatus = 'NEW' | 'QUESTIONNAIRE_SENT' | 'QUESTIONNAIRE_COMPLETED' | 'ACCEPTED' | 'REJECTED';

export const LEAD_STATUS_LABEL: Record<LeadStatus, string> = {
  NEW: 'Nueva',
  QUESTIONNAIRE_SENT: 'Cuestionario enviado',
  QUESTIONNAIRE_COMPLETED: 'Por revisar',
  ACCEPTED: 'Aceptada',
  REJECTED: 'Rechazada',
};

export interface ContactRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string | null;
  objective: string;
  planId?: number | null;
  consent: boolean;
  /** Honeypot: must stay empty. */
  website?: string;
}

export interface Questionnaire {
  birthDate: string;
  sex: 'MUJER' | 'HOMBRE' | 'OTRO';
  heightCm: number;
  weightKg: number;
  occupation?: string | null;
  activityLevel: 'SEDENTARIO' | 'LIGERO' | 'ACTIVO' | 'MUY_ACTIVO';
  mainGoal: 'PERDER_GRASA' | 'GANAR_MUSCULO' | 'RECOMPOSICION' | 'RENDIMIENTO' | 'SALUD';
  goalDetails?: string | null;
  experience: 'NINGUNA' | 'MENOS_1' | 'ENTRE_1_3' | 'MAS_3';
  currentTraining?: string | null;
  hasInjuries: boolean;
  injuries?: string | null;
  hasMedicalConditions: boolean;
  medicalConditions?: string | null;
  medication?: string | null;
  surgeries?: string | null;
  recentBloodTest?: boolean | null;
  daysPerWeek: number;
  minutesPerSession: number;
  trainingPlace: 'GIMNASIO' | 'CASA' | 'EXTERIOR' | 'MIXTO';
  equipment?: string | null;
  dietType: 'OMNIVORA' | 'VEGETARIANA' | 'VEGANA' | 'OTRA';
  allergies?: string | null;
  mealsPerDay?: number | null;
  dislikedFoods?: string | null;
  sleepHours?: number | null;
  stressLevel?: number | null;
  alcoholTobacco?: string | null;
  planId?: number | null;
  billingPeriod?: BillingPeriod | null;
  howFound?: string | null;
  comments?: string | null;
  healthConsent: boolean;
}

export const Q_LABELS = {
  sex: { MUJER: 'Mujer', HOMBRE: 'Hombre', OTRO: 'Otro / prefiero no decirlo' },
  activityLevel: {
    SEDENTARIO: 'Sedentaria (trabajo sentado)',
    LIGERO: 'Ligera (camino algo cada día)',
    ACTIVO: 'Activa (trabajo de pie / me muevo mucho)',
    MUY_ACTIVO: 'Muy activa (trabajo físico)',
  },
  mainGoal: {
    PERDER_GRASA: 'Perder grasa',
    GANAR_MUSCULO: 'Ganar músculo',
    RECOMPOSICION: 'Recomposición (perder grasa y ganar músculo)',
    RENDIMIENTO: 'Mejorar mi rendimiento',
    SALUD: 'Salud y bienestar',
  },
  experience: { NINGUNA: 'Nunca he entrenado', MENOS_1: 'Menos de 1 año', ENTRE_1_3: 'Entre 1 y 3 años', MAS_3: 'Más de 3 años' },
  trainingPlace: { GIMNASIO: 'Gimnasio', CASA: 'En casa', EXTERIOR: 'Al aire libre', MIXTO: 'Mixto' },
  dietType: { OMNIVORA: 'Omnívora (como de todo)', VEGETARIANA: 'Vegetariana', VEGANA: 'Vegana', OTRA: 'Otra' },
} as const;

export interface LeadSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  objective: string;
  preferredPlanId: number | null;
  preferredPlanName: string | null;
  status: LeadStatus;
  createdAt: string;
  questionnaireSentAt: string | null;
  questionnaireCompletedAt: string | null;
  decidedAt: string | null;
  clientId: number | null;
  healthFlag: boolean;
}

export interface LeadDetail {
  lead: LeadSummary;
  questionnaire: Questionnaire | null;
  questionnairePlanName: string | null;
  questionnaireExpired: boolean;
  questionnaireExpiresAt: string | null;
  trainerNote: string | null;
  decisionMessage: string | null;
  activationPending: boolean;
}

export interface LeadCounts {
  newRequests: number;
  questionnaireSent: number;
  toReview: number;
  accepted: number;
  rejected: number;
}

export interface ApproveLeadRequest {
  planId: number;
  billingPeriod: BillingPeriod;
  customPrice?: number | null;
  customPriceNote?: string | null;
  message?: string | null;
}

export interface QuestionnaireInfo {
  firstName: string | null;
  state: 'OPEN' | 'EXPIRED' | 'COMPLETED' | 'CLOSED';
  expiresAt: string | null;
  preferredPlanId: number | null;
}

export interface ActivationInfo {
  firstName: string;
  username: string;
  email: string;
}
