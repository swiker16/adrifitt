export type AnalysisStatus = 'UPLOADED' | 'REVIEWED';

export interface ClientAnalysis {
  id: number;
  clientId: number;
  title: string;
  analysisDate: string;
  clientComment?: string;
  trainerInternalNote?: string;
  uploadedAt: string;
  reviewedAt?: string;
  status: AnalysisStatus;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  downloadUrl: string;
  viewUrl: string;
}

export interface UploadAnalysisRequest {
  title: string;
  analysisDate: string;
  clientComment?: string;
}

export interface ReviewAnalysisRequest {
  trainerInternalNote?: string;
}
