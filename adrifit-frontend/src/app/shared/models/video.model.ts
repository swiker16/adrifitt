export type VideoSource = 'CLIENT' | 'TRAINER';

/** Exercise technique video: the client's execution (to correct) or a trainer demonstration. */
export interface TechniqueVideo {
  id: number;
  clientId: number;
  clientName: string | null;
  source: VideoSource;
  exerciseName: string;
  note: string | null;
  contentType: string;
  fileSize: number;
  durationSeconds: number | null;
  feedback: string | null;
  reviewedAt: string | null;
  /** For the client: the trainer's video or correction is new. */
  unseen: boolean;
  createdAt: string;
}

export interface VideoStreamLink {
  url: string;
  downloadUrl: string;
  expiresAt: string;
}

export interface VideoUpload {
  file: File;
  exerciseName: string;
  note?: string | null;
  durationSeconds?: number | null;
}
