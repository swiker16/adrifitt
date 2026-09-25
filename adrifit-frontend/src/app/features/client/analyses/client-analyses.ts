import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AnalysisService } from '../../../core/services/analysis.service';
import { ClientAnalysis } from '../../../shared/models/analysis.model';

@Component({
  selector: 'app-client-analyses',
  imports: [DatePipe, FormsModule, MatIconModule],
  templateUrl: './client-analyses.html',
  styleUrl: './client-analyses.scss',
})
export class ClientAnalyses {
  private readonly service = inject(AnalysisService);

  readonly analyses = signal<ClientAnalysis[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showUpload = signal(false);
  readonly uploading = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly title = signal('');
  readonly analysisDate = signal('');
  readonly clientComment = signal('');
  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.getMyAnalyses().subscribe({
      next: (list) => { this.analyses.set(list); this.loading.set(false); },
      error: () => { this.error.set('Error al cargar las analíticas.'); this.loading.set(false); },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    this.fileError.set(null);
    if (!file) { this.selectedFile.set(null); return; }
    if (file.type !== 'application/pdf') {
      this.fileError.set('Solo se permiten archivos PDF.');
      this.selectedFile.set(null);
      input.value = '';
      return;
    }
    if (file.size > 15 * 1024 * 1024) {
      this.fileError.set('El archivo supera los 15 MB.');
      this.selectedFile.set(null);
      input.value = '';
      return;
    }
    this.selectedFile.set(file);
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file || !this.title().trim() || !this.analysisDate()) return;
    this.uploading.set(true);
    this.uploadError.set(null);
    this.service.upload(this.title().trim(), this.analysisDate(), this.clientComment().trim() || null, file).subscribe({
      next: (a) => {
        this.analyses.update(list => [a, ...list]);
        this.uploading.set(false);
        this.showUpload.set(false);
        this.resetForm();
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(err?.error?.message ?? 'Error al subir la analítica.');
      },
    });
  }

  open(analysis: ClientAnalysis): void {
    this.service.downloadContent(analysis.id, 'inline', 'client').subscribe(blob => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    });
  }

  download(analysis: ClientAnalysis): void {
    this.service.downloadContent(analysis.id, 'attachment', 'client').subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = analysis.originalFileName;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  delete(analysis: ClientAnalysis): void {
    if (!confirm(`¿Eliminar la analítica "${analysis.title}"?`)) return;
    this.deletingId.set(analysis.id);
    this.service.deleteMyAnalysis(analysis.id).subscribe({
      next: () => {
        this.analyses.update(list => list.filter(a => a.id !== analysis.id));
        this.deletingId.set(null);
      },
      error: (err) => {
        alert(err?.error?.message ?? 'No se pudo eliminar la analítica.');
        this.deletingId.set(null);
      },
    });
  }

  statusLabel(status: string): string {
    return status === 'REVIEWED' ? 'Revisada' : 'Pendiente de revisión';
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  resetForm(): void {
    this.title.set('');
    this.analysisDate.set('');
    this.clientComment.set('');
    this.selectedFile.set(null);
    this.fileError.set(null);
  }
}
