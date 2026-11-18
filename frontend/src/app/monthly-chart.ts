import { Component, Input } from '@angular/core';
import { MonthlyPoint } from './api';

interface ChartBar {
  label: string;
  title: string;
  x: number;
  incomeY: number;
  incomeHeight: number;
  expenseY: number;
  expenseHeight: number;
  centerX: number;
}

@Component({
  selector: 'app-monthly-chart',
  template: `
    <figure class="chart" role="img"
            [attr.aria-label]="'Gráfico de receitas e despesas por mês entre ' + from + ' e ' + to">
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="xMidYMid meet">
        <line x1="0" y1="0" x2="0" [attr.y2]="height" class="chart-axis" />
        <line x1="0" [attr.y1]="height" [attr.x2]="width" [attr.y2]="height" class="chart-axis" />
        @for (bar of bars; track bar.label) {
          <g>
            <rect class="chart-income" [attr.x]="bar.x" [attr.y]="bar.incomeY"
                  [attr.width]="barWidth" [attr.height]="bar.incomeHeight">
              <title>{{ bar.title }}</title>
            </rect>
            <rect class="chart-expense" [attr.x]="bar.x + barWidth + 2"
                  [attr.y]="bar.expenseY" [attr.width]="barWidth" [attr.height]="bar.expenseHeight">
              <title>{{ bar.title }}</title>
            </rect>
            <text class="chart-label" [attr.x]="bar.centerX" [attr.y]="height + 14">
              {{ bar.label }}
            </text>
          </g>
        }
      </svg>
      <figcaption class="muted">
        <span class="chart-key"><i class="chart-key-income"></i> Receitas realizadas</span>
        <span class="chart-key"><i class="chart-key-expense"></i> Despesas realizadas</span>
      </figcaption>
    </figure>
  `,
  styles: [`
    .chart { margin: 0; }
    svg { width: 100%; height: auto; overflow: visible; }
    .chart-axis { stroke: #cbd5e1; stroke-width: 1; }
    .chart-income { fill: #00abe4; }
    .chart-expense { fill: #1e3a5f; }
    .chart-label { font-size: 11px; fill: #475569; text-anchor: middle; }
    figcaption { display: flex; gap: 1rem; flex-wrap: wrap; margin-top: 0.5rem; font-size: 0.85rem; }
    .chart-key { display: inline-flex; align-items: center; gap: 0.35rem; }
    .chart-key-income, .chart-key-expense { width: 0.75rem; height: 0.75rem; border-radius: 2px; display: inline-block; }
    .chart-key-income { background: #00abe4; }
    .chart-key-expense { background: #1e3a5f; }
  `]
})
export class MonthlyChart {
  @Input({ required: true }) points: MonthlyPoint[] = [];
  @Input() from = '';
  @Input() to = '';

  readonly width = 600;
  readonly height = 220;
  readonly barWidth = 12;

  get bars(): ChartBar[] {
    const points = this.points ?? [];
    const max = points.reduce((highest, point) => Math.max(highest,
      point.realizedIncome, point.realizedExpenses), 0);
    const scale = max > 0 ? (this.height - 24) / max : 0;
    const slot = points.length > 0 ? this.width / points.length : this.width;
    return points.map((point, index) => {
      const incomeHeight = Math.round(point.realizedIncome * scale);
      const expenseHeight = Math.round(point.realizedExpenses * scale);
      const left = index * slot + slot / 2 - this.barWidth - 1;
      return {
        label: point.month.slice(5),
        title: `${point.month}: receitas ${point.realizedIncome}, despesas ${point.realizedExpenses}`,
        x: Math.max(0, left),
        incomeY: this.height - incomeHeight,
        incomeHeight,
        expenseY: this.height - expenseHeight,
        expenseHeight,
        centerX: index * slot + slot / 2
      };
    });
  }
}
