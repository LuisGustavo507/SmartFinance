import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { money, monthLabel } from '../format';
import { MoneyField } from '../money-field';
import { DateField } from '../date-field';

@Component({
  selector: 'app-transfers-page',
  imports: [FormsModule, ButtonModule, CardModule, InputNumberModule, InputTextModule, MoneyField, DateField],
  templateUrl: './transfers-page.html'
})
export class TransfersPage {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
}
