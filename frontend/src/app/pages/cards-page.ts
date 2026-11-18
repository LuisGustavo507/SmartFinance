import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { money, monthLabel, shortDate } from '../format';
import { DateField } from '../date-field';
import { MoneyField } from '../money-field';

@Component({
  selector: 'app-cards-page',
  imports: [FormsModule, RouterLink, ButtonModule, CardModule, InputNumberModule, InputTextModule, DateField, MoneyField],
  templateUrl: './cards-page.html'
})
export class CardsPage implements OnInit {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;

  ngOnInit(): void {
    void this.store.loadCards();
  }

  async loadStatements(): Promise<void> {
    await this.store.loadCards();
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PROJETADA': return 'Projetada';
      case 'ABERTA': return 'Aberta';
      case 'FECHADA': return 'Fechada';
      case 'PAGA': return 'Paga';
      case 'ATRASADA': return 'Atrasada';
      default: return status;
    }
  }

  necessityLabel(value: string): string {
    switch (value) {
      case 'NECESSARIA': return 'Necessária';
      case 'NEUTRA': return 'Neutra';
      case 'FUTIL': return 'Fútil';
      default: return value;
    }
  }
}
