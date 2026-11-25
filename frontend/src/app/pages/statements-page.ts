import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CardInstallment, CardStatement, Counterparty, ReversalCredit } from '../api';
import { FinanceStore } from '../finance-store';
import { money, monthLabel, shortDate } from '../format';
import { DateField } from '../date-field';
import { MonthNavigation } from '../month-navigation';
import { MaintenanceDialog } from '../maintenance-dialog';

@Component({
  selector: 'app-statements-page',
  imports: [FormsModule, RouterLink, ButtonModule, CardModule, DateField, MonthNavigation, MaintenanceDialog],
  templateUrl: './statements-page.html',
  styleUrl: './statements-page.css'
})
export class StatementsPage implements OnInit {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;
  selectedInstallment: CardInstallment | null = null;
  readonly entityDirty = (): boolean => !!this.selectedInstallment &&
    this.store.purchaseEntitySelection !== this.selectedInstallment.entityId;

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

  entityChoices(installment: CardInstallment): Counterparty[] {
    return this.store.counterparties().filter(entity =>
      entity.active || entity.id === installment.entityId);
  }

  cardChanged(): void {
    this.store.paymentStatementId = null;
    this.closeInstallment();
  }

  openInstallment(installment: CardInstallment): void {
    this.selectedInstallment = installment;
    this.store.startPurchaseEntityEdit(installment);
  }

  closeInstallment(): void {
    this.selectedInstallment = null;
    this.store.editingPurchaseEntityId = null;
  }

  async saveEntity(): Promise<void> {
    await this.store.savePurchaseEntity();
    if (this.store.editingPurchaseEntityId === null) this.closeInstallment();
  }

  async reversePurchase(): Promise<void> {
    const purchaseId = this.selectedInstallment?.purchaseId;
    if (purchaseId === undefined) return;
    this.store.notice.set('');
    await this.store.reverseCardPurchase(purchaseId);
    if (this.store.notice() === 'Compra estornada.') this.closeInstallment();
  }

  changeMonth(month: string): void {
    this.store.statementMonth = month;
    this.loadStatements();
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
