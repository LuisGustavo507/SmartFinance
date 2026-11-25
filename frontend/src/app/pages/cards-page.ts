import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { CreditCard } from '../api';
import { MaintenanceDialog } from '../maintenance-dialog';
import { MoneyField } from '../money-field';

@Component({
  selector: 'app-cards-page',
  imports: [
    FormsModule, ButtonModule, CardModule, InputNumberModule, InputTextModule,
    MaintenanceDialog, MoneyField
  ],
  templateUrl: './cards-page.html'
})
export class CardsPage implements OnInit {
  readonly store = inject(FinanceStore);

  ngOnInit(): void {
    void this.store.loadCardCatalog();
  }

  editedCard(): CreditCard | undefined {
    return this.store.cards().find(card => card.id === this.store.editingCardId);
  }

  readonly cardDirty = (): boolean => {
    const card = this.editedCard();
    return !!card && (this.store.editingCardName.trim() !== card.name ||
      this.store.editingCardInstitution !== (card.institutionName ?? '') ||
      this.store.editingClosingDay !== card.closingDay ||
      this.store.editingDueDay !== card.dueDay ||
      this.store.editingCardLimit !== card.creditLimit ||
      this.store.editingCardWalletId !== card.defaultPaymentWalletId);
  };

  openEditor(card: CreditCard): void {
    this.store.beginCardEdit(card);
  }

  closeEditor(): void {
    this.store.editingCardId = null;
  }

  async saveCard(): Promise<void> {
    await this.store.updateCard();
  }
}
