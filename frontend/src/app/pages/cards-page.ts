import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { MoneyField } from '../money-field';

@Component({
  selector: 'app-cards-page',
  imports: [FormsModule, ButtonModule, CardModule, InputNumberModule, InputTextModule, MoneyField],
  templateUrl: './cards-page.html'
})
export class CardsPage implements OnInit {
  readonly store = inject(FinanceStore);

  ngOnInit(): void {
    void this.store.loadCardCatalog();
  }
}
