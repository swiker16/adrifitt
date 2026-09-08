import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-trainer-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule],
  templateUrl: './trainer-layout.html',
  styleUrl: './trainer-layout.scss',
})
export class TrainerLayout {
  private readonly auth = inject(AuthService);

  readonly username = computed(() => this.auth.user()?.username ?? 'Entrenador');
  readonly initials = computed(() => (this.auth.user()?.username ?? 'T').slice(0, 2));
  readonly menuOpen = signal(false);

  toggleMenu(): void { this.menuOpen.update(v => !v); }
  closeMenu(): void  { this.menuOpen.set(false); }

  logout(): void {
    this.auth.logout();
  }
}
