import { Component, computed, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ThemeMode, ThemeService } from '../../core/services/theme.service';

interface Option {
  mode: ThemeMode;
  icon: string;
  label: string;
}

/**
 * Theme picker.
 * - `segmented`: Claro · Auto · Oscuro (settings pages).
 * - `sidebar`: same, styled for the dark sidebar.
 * - `icon`: one round button that cycles (landing, login).
 */
@Component({
  selector: 'app-theme-toggle',
  imports: [MatIconModule],
  template: `
    @if (variant() === 'icon') {
      <button type="button" class="icon-toggle" (click)="theme.cycle()" [attr.aria-label]="'Tema: ' + current().label + '. Cambiar tema'" [title]="'Tema: ' + current().label">
        <mat-icon>{{ current().icon }}</mat-icon>
      </button>
    } @else {
      <div class="seg" [class.sidebar]="variant() === 'sidebar'" role="radiogroup" aria-label="Tema de la aplicación">
        @for (o of options; track o.mode) {
          <button type="button" role="radio" [attr.aria-checked]="theme.mode() === o.mode" [class.active]="theme.mode() === o.mode"
                  (click)="theme.setMode(o.mode)" [title]="o.mode === 'system' ? 'Automático: como tu dispositivo' : o.label">
            <mat-icon>{{ o.icon }}</mat-icon><span>{{ o.label }}</span>
          </button>
        }
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .seg {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 2px; padding: 3px; border-radius: 12px;
      background: var(--ink-100); border: 1px solid var(--border);
    }
    .seg button {
      min-width: 0; overflow: hidden; white-space: nowrap;
      display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 36px; padding: 6px 8px;
      border: 0; border-radius: 9px; background: transparent; color: var(--text-muted); font: inherit; font-size: .8rem;
      font-weight: 600; cursor: pointer; transition: background .15s, color .15s;
    }
    .seg button mat-icon { font-size: 17px; width: 17px; height: 17px; }
    .seg button:hover { color: var(--text); }
    .seg button.active { background: var(--surface); color: var(--text); box-shadow: var(--shadow-xs); }
    .seg button.active mat-icon { color: var(--brand); }

    /* Sidebar: always on a dark background */
    .seg.sidebar { background: rgba(255, 255, 255, .05); border-color: rgba(255, 255, 255, .08); }
    .seg.sidebar button { color: rgba(255, 255, 255, .6); min-height: 32px; min-width: 0; padding: 5px 2px; gap: 3px; font-size: .72rem; }
    .seg.sidebar button mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .seg.sidebar button:hover { color: #fff; }
    .seg.sidebar button.active { background: rgba(255, 255, 255, .12); color: #fff; box-shadow: none; }

    .icon-toggle {
      width: 40px; height: 40px; display: grid; place-items: center; border-radius: 12px; cursor: pointer;
      border: 1px solid var(--border); background: var(--surface); color: var(--text); transition: background .15s, border-color .15s;
    }
    .icon-toggle:hover { border-color: var(--brand); color: var(--brand); }
    .icon-toggle mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `,
})
export class ThemeToggle {
  readonly theme = inject(ThemeService);
  readonly variant = input<'segmented' | 'sidebar' | 'icon'>('segmented');

  readonly options: Option[] = [
    { mode: 'light', icon: 'light_mode', label: 'Claro' },
    { mode: 'system', icon: 'brightness_auto', label: 'Auto' },
    { mode: 'dark', icon: 'dark_mode', label: 'Oscuro' },
  ];
  readonly current = computed(() => this.options.find((o) => o.mode === this.theme.mode())!);
}
