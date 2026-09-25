export type PhotoPose = 'FRONT' | 'SIDE' | 'BACK' | 'OTHER';

export interface ProgressPhoto {
  id: number;
  clientId: number;
  takenOn: string;
  pose: PhotoPose;
  notes: string | null;
  trainerComment: string | null;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
  /** API path (e.g. /api/photos/3/content); needs the auth header → use <app-secure-img>. */
  contentUrl: string;
}

export const POSE_LABEL: Record<PhotoPose, string> = {
  FRONT: 'Frontal',
  SIDE: 'Perfil',
  BACK: 'Espalda',
  OTHER: 'Otra',
};
