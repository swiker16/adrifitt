import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Role } from '../../shared/models/auth.model';

export function roleGuard(allowed: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const role = auth.role();
    if (role && allowed.includes(role)) {
      return true;
    }
    auth.redirectByRole();
    return false;
  };
}
