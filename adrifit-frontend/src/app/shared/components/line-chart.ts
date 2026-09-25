import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface ChartPoint {
  /** x label (already formatted, e.g. "12/09") */
  label: string;
  value: number | null;
}

/**
 * Minimal responsive SVG line chart (no external libs). Null values are skipped.
 */
@Component({
  selector: 'app-line-chart',
  imports: [DecimalPipe],
  template: `
    @if (valid().length < 2) {
      <div class="chart-empty">{{ emptyText() }}</div>
    } @else {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" class="chart" role="img" [attr.aria-label]="unit()">
        @for (t of ticks(); track $index) {
          <line [attr.x1]="PAD_L" [attr.x2]="W - PAD_R" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
          <text [attr.x]="PAD_L - 6" [attr.y]="t.y + 4" class="tick" text-anchor="end">{{ t.value | number: '1.0-1' }}</text>
        }
        <path [attr.d]="area()" class="area" [style.fill]="color()" />
        <path [attr.d]="line()" class="line" [style.stroke]="color()" />
        @for (p of coords(); track $index) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3.5" [style.fill]="color()">
            <title>{{ p.label }}: {{ p.value | number: '1.0-2' }} {{ unit() }}</title>
          </circle>
        }
        @for (p of xLabels(); track $index) {
          <text [attr.x]="p.x" [attr.y]="H - 6" class="tick" text-anchor="middle">{{ p.label }}</text>
        }
      </svg>
    }
  `,
  styles: `
    :host { display: block; width: 100%; }
    .chart { width: 100%; height: 220px; overflow: visible; }
    .grid { stroke: #eef2f7; stroke-width: 1; }
    .tick { fill: #94a3b8; font-size: 11px; font-family: Inter, sans-serif; }
    .line { fill: none; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; vector-effect: non-scaling-stroke; }
    .area { opacity: 0.08; stroke: none; }
    .chart-empty { color: #94a3b8; font-size: 0.88rem; padding: 36px 0; text-align: center; }
  `,
})
export class LineChart {
  readonly points = input<ChartPoint[]>([]);
  readonly unit = input('');
  readonly color = input('#FF7A1A');
  readonly emptyText = input('Necesitas al menos dos registros para ver la evolución.');

  readonly W = 640;
  readonly H = 220;
  readonly PAD_L = 42;
  readonly PAD_R = 12;
  private readonly PAD_T = 12;
  private readonly PAD_B = 26;

  readonly valid = computed(() => this.points().filter((p) => p.value !== null && p.value !== undefined));

  private readonly range = computed(() => {
    const values = this.valid().map((p) => p.value as number);
    let min = Math.min(...values);
    let max = Math.max(...values);
    if (min === max) {
      min -= 1;
      max += 1;
    }
    const pad = (max - min) * 0.1;
    return { min: min - pad, max: max + pad };
  });

  readonly coords = computed(() => {
    const pts = this.valid();
    const { min, max } = this.range();
    const w = this.W - this.PAD_L - this.PAD_R;
    const h = this.H - this.PAD_T - this.PAD_B;
    return pts.map((p, i) => ({
      x: this.PAD_L + (pts.length === 1 ? w / 2 : (i * w) / (pts.length - 1)),
      y: this.PAD_T + h - (((p.value as number) - min) / (max - min)) * h,
      label: p.label,
      value: p.value as number,
    }));
  });

  readonly line = computed(() => this.coords().map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x},${c.y}`).join(' '));

  readonly area = computed(() => {
    const c = this.coords();
    if (c.length === 0) return '';
    const bottom = this.H - this.PAD_B;
    return `${this.line()} L${c[c.length - 1].x},${bottom} L${c[0].x},${bottom} Z`;
  });

  readonly ticks = computed(() => {
    const { min, max } = this.range();
    const h = this.H - this.PAD_T - this.PAD_B;
    return [0, 0.5, 1].map((f) => ({ y: this.PAD_T + h - f * h, value: min + f * (max - min) }));
  });

  /** At most ~6 x labels to avoid overlap. */
  readonly xLabels = computed(() => {
    const c = this.coords();
    const step = Math.max(1, Math.ceil(c.length / 6));
    return c.filter((_, i) => i % step === 0 || i === c.length - 1);
  });
}
