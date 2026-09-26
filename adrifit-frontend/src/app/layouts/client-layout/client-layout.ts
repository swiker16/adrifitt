import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { filter, interval, merge, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DeviceSetupPrompt } from '../../shared/components/device-setup-prompt';

@Component({
  selector: 'app-client-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule, DeviceSetupPrompt],
  templateUrl: './client-layout.html',
  styleUrl: './client-layout.scss',
})
export class ClientLayout {
  private readonly auth = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly username = computed(() => this.auth.user()?.username ?? 'Cliente');
  readonly initials = computed(() => (this.auth.user()?.username ?? 'C').slice(0, 2));
  readonly menuOpen = signal(false);

  readonly unreadMessages = signal(0);
  readonly pendingPayments = signal(0);

  constructor() {
    // Badges: refreshed every minute and after each navigation (skipped while the
    // temporary password has not been changed: every other endpoint is still reachable
    // but the menu is not useful yet).
    merge(interval(60000).pipe(startWith(0)), this.router.events.pipe(filter((e) => e instanceof NavigationEnd)))
      .pipe(
        filter(() => !this.auth.mustChangePassword()),
        switchMap(() => this.dashboardService.getClientDashboard()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (d) => {
          this.unreadMessages.set(d.unreadMessages ?? 0);
          this.pendingPayments.set(d.pendingPayments ?? 0);
        },
        error: () => undefined,
      });
  }

  toggleMenu(): void { this.menuOpen.update(v => !v); }
  closeMenu(): void  { this.menuOpen.set(false); }

  logout(): void {
    this.auth.logout();
  }
}
