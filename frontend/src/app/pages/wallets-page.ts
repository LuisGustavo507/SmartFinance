import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { WalletType } from '../api';
import { money, shortDate } from '../format';
import { DateField } from '../date-field';
import { MoneyField } from '../money-field';

@Component({
  selector: 'app-wallets-page',
  imports: [FormsModule, ButtonModule, CardModule, InputNumberModule, InputTextModule, DateField, MoneyField],
  templateUrl: './wallets-page.html'
})
export class WalletsPage {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly shortDate = shortDate;

  typeLabel(type: WalletType): string {
    return type === 'CARTEIRA_DINHEIRO' ? 'Carteira em dinheiro'
      : type === 'CONTA_CORRENTE' ? 'Conta corrente'
      : type === 'POUVANCA' ? 'Poupança'
      : type === 'CONTA_PAGAMENTO' ? 'Conta de pagamento' : 'Outra conta';
  }
}
