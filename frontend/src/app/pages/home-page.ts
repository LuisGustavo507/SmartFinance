import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { FinanceStore } from '../finance-store';
import { money, monthLabel, shortDate } from '../format';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, ButtonModule, CardModule],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css'
})
export class HomePage {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;

  activeWalletsLabel(): string {
    const count = this.store.activeWallets().length;
    return count === 1 ? '1 carteira ativa' : `${count} carteiras ativas`;
  }
}
