import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { filter, interval, merge, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DeviceSetupPrompt } from '../../shared/components/device-setup-prompt';
import { ThemeToggle } from '../../shared/components/theme-toggle';
import { InstallButton } from '../../shared/components/install-button';
import { InstallService } from '../../core/services/install.service';

@Component({
  selector: 'app-trainer-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule, DeviceSetupPrompt, ThemeToggle, InstallButton],
  templateUrl: './trainer-layout.html',
  styleUrl: './trainer-layout.scss',
})
export class TrainerLayout {
  private readonly auth = inject(AuthService);
  readonly install = inject(InstallService);
  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly username = computed(() => this.auth.user()?.username ?? 'Entrenador');
  readonly initials = computed(() => (this.auth.user()?.username ?? 'T').slice(0, 2));
  readonly menuOpen = signal(false);

  readonly unreadMessages = signal(0);
  readonly tasksDue = signal(0);
  readonly pendingAnalyses = signal(0);
  readonly overduePayments = signal(0);
  readonly pendingVideos = signal(0);
  readonly pendingLeads = signal(0);
  readonly totalBadges = computed(
    () => this.unreadMessages() + this.tasksDue() + this.pendingAnalyses() + this.overduePayments() + this.pendingVideos() + this.pendingLeads()
  );

  constructor() {
    // Sidebar badges: refreshed every minute and after each navigation.
    const navigations = this.router.events.pipe(filter((e) => e instanceof NavigationEnd));
    interval(60000)
      .pipe(
        startWith(0),
        switchMap(() => this.dashboardService.getTrainerDashboard()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({ next: (d) => this.applyCounts(d), error: () => undefined });
    merge(navigations, this.dashboardService.changed$)
      .pipe(
        switchMap(() => this.dashboardService.getTrainerDashboard()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({ next: (d) => this.applyCounts(d), error: () => undefined });
  }

  private applyCounts(d: { unreadMessages: number; tasksDue: number; pendingAnalyses: number; overduePaymentsCount: number; pendingVideos: number; pendingLeads: number }): void {
    this.unreadMessages.set(d.unreadMessages ?? 0);
    this.tasksDue.set(d.tasksDue ?? 0);
    this.pendingAnalyses.set(d.pendingAnalyses ?? 0);
    this.overduePayments.set(d.overduePaymentsCount ?? 0);
    this.pendingVideos.set(d.pendingVideos ?? 0);
    this.pendingLeads.set(d.pendingLeads ?? 0);
  }

  toggleMenu(): void { this.menuOpen.update(v => !v); }
  closeMenu(): void  { this.menuOpen.set(false); }

  logout(): void {
    this.auth.logout();
  }
}
