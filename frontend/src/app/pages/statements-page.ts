import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CardInstallment, CardStatement, ReversalCredit } from '../api';
import { FinanceStore } from '../finance-store';
import { money, monthLabel, shortDate } from '../format';
import { DateField } from '../date-field';

@Component({
  selector: 'app-statements-page',
  imports: [FormsModule, RouterLink, ButtonModule, CardModule, DateField],
  templateUrl: './statements-page.html',
  styleUrl: './statements-page.css'
})
export class StatementsPage implements OnInit {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;

  ngOnInit(): void {
    this.cardChanged();
    void this.store.loadCards();
  }

  selectedStatement(): CardStatement | undefined {
    return this.store.cardStatements().find(statement =>
      statement.cardId === this.store.statementCardId &&
      statement.referenceMonth === this.store.statementMonth);
  }

  activeInstallments(statement: CardStatement): CardInstallment[] {
    return statement.installments.filter(installment => installment.status === 'ATIVA');
  }

  historicalInstallments(statement: CardStatement): CardInstallment[] {
    return statement.installments.filter(installment => installment.status !== 'ATIVA');
  }

  selectedCardCredits(): ReversalCredit[] {
    return this.store.cardCredits().filter(credit => credit.cardId === this.store.statementCardId);
  }

  cardChanged(): void {
    this.store.paymentStatementId = null;
    this.store.editingPurchaseEntityId = null;
  }

  loadStatements(): void {
    this.cardChanged();
    void this.store.loadCards();
  }

  statusLabel(status: CardStatement['status']): string {
    switch (status) {
      case 'PROJETADA': return 'Projetada';
      case 'ABERTA': return 'Aberta';
      case 'FECHADA': return 'Fechada';
      case 'PAGA': return 'Paga';
      case 'ATRASADA': return 'Atrasada';
    }
  }
}
