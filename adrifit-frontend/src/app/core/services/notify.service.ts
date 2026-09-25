import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { apiErrorMessage } from '../../shared/utils/download';

/** Small toast notifications (success / error) shown at the bottom of the screen. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3500, panelClass: 'snack-success' });
  }

  error(errOrMessage: unknown, fallback?: string): void {
    const message = typeof errOrMessage === 'string' ? errOrMessage : apiErrorMessage(errOrMessage, fallback);
    this.snackBar.open(message, 'Cerrar', { duration: 6000, panelClass: 'snack-error' });
  }
}
