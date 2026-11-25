import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DateField } from './date-field';
import { monthLabel } from './format';

@Component({
  selector: 'app-month-navigation',
  imports: [FormsModule, ButtonModule, DateField],
  template: `
    <div class="month-navigation">
      <p-button icon="pi pi-chevron-left" [ariaLabel]="'Mês anterior a ' + monthLabel(month)"
                severity="secondary" [outlined]="true"
                [style]="{ width: '44px', height: '44px' }" (onClick)="shift(-1)" />
      <div class="month-navigation-current">
        <strong aria-live="polite">{{ monthLabel(month) }}</strong>
        <app-date-field [inputId]="inputId" [month]="true" [ngModel]="month"
                        (ngModelChange)="change($event)" />
      </div>
      <p-button icon="pi pi-chevron-right" [ariaLabel]="'Mês seguinte a ' + monthLabel(month)"
                severity="secondary" [outlined]="true"
                [style]="{ width: '44px', height: '44px' }" (onClick)="shift(1)" />
    </div>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .month-navigation {
      display: grid;
      grid-template-columns: 2.75rem minmax(0, 1fr) 2.75rem;
      align-items: center;
      gap: .5rem;
      width: 100%;
    }
    .month-navigation-current { min-width: 0; text-align: center; }
    .month-navigation-current strong { display: block; margin-bottom: .25rem; }
  `]
})
export class MonthNavigation {
  @Input({ required: true }) inputId = '';
  @Input({ required: true }) month = '';
  @Output() readonly monthChange = new EventEmitter<string>();
  readonly monthLabel = monthLabel;

  change(month: string): void {
    if (/^\d{4}-(0[1-9]|1[0-2])$/.test(month) && month !== this.month) {
      this.monthChange.emit(month);
    }
  }

  shift(delta: -1 | 1): void {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(this.month)) return;
    const [year, month] = this.month.split('-').map(Number);
    const shifted = year * 12 + month - 1 + delta;
    const nextYear = Math.floor(shifted / 12);
    const nextMonth = shifted - nextYear * 12 + 1;
    this.monthChange.emit(`${nextYear}-${String(nextMonth).padStart(2, '0')}`);
  }
}
