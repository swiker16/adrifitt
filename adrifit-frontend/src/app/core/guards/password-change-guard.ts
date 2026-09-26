import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Clients created by the trainer log in with a temporary password: until they change it they
 * can only reach their profile page.
 */
export const passwordChangeGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.mustChangePassword() || state.url.startsWith('/client/profile')) {
    return true;
  }
  return router.createUrlTree(['/client/profile']);
};
