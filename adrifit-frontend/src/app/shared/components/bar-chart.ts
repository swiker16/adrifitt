import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

export interface BarPoint {
  label: string;
  value: number;
}

/** Minimal responsive vertical bar chart (SVG, no external libs). */
@Component({
  selector: 'app-bar-chart',
  imports: [DecimalPipe],
  template: `
    @if (!hasData()) {
      <div class="chart-empty">{{ emptyText() }}</div>
    } @else {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" class="chart" role="img">
        <line [attr.x1]="0" [attr.x2]="W" [attr.y1]="H - PAD_B" [attr.y2]="H - PAD_B" class="axis" />
        @for (b of bars(); track $index) {
          <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="4" [style.fill]="color()">
            <title>{{ b.label }}: {{ b.value | number: '1.0-2' }} {{ unit() }}</title>
          </rect>
          <text [attr.x]="b.x + b.w / 2" [attr.y]="H - 8" class="tick" text-anchor="middle">{{ b.label }}</text>
        }
      </svg>
    }
  `,
  styles: `
    :host { display: block; width: 100%; }
    .chart { width: 100%; height: 200px; }
    .axis { stroke: #e5e7eb; }
    .tick { fill: #94a3b8; font-size: 11px; font-family: Inter, sans-serif; }
    .chart-empty { color: #94a3b8; font-size: 0.88rem; padding: 36px 0; text-align: center; }
  `,
})
export class BarChart {
  readonly points = input<BarPoint[]>([]);
  readonly unit = input('');
  readonly color = input('#FF7A1A');
  readonly emptyText = input('Sin datos todavía.');

  readonly W = 640;
  readonly H = 200;
  readonly PAD_B = 24;

  readonly hasData = computed(() => this.points().some((p) => p.value > 0));

  readonly bars = computed(() => {
    const pts = this.points();
    const max = Math.max(...pts.map((p) => p.value), 1);
    const slot = this.W / Math.max(pts.length, 1);
    const w = Math.max(6, slot * 0.6);
    const h = this.H - this.PAD_B - 8;
    return pts.map((p, i) => {
      const bh = (p.value / max) * h;
      return { x: i * slot + (slot - w) / 2, y: this.H - this.PAD_B - bh, w, h: bh, label: p.label, value: p.value };
    });
  });
}
