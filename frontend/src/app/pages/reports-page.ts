import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { MonthlyChart } from '../monthly-chart';
import { money, monthLabel } from '../format';
import { DateField } from '../date-field';

@Component({
  selector: 'app-reports-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, MonthlyChart, DateField],
  templateUrl: './reports-page.html'
})
export class ReportsPage {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
}
