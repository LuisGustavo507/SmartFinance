import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { Counterparty, Entry } from '../api';
import { categoryRows, money, monthLabel, necessityLabel, shortDate } from '../format';
import { DateField } from '../date-field';
import { MaintenanceDialog } from '../maintenance-dialog';
import { MoneyField } from '../money-field';
import { MonthNavigation } from '../month-navigation';

@Component({
  selector: 'app-entries-page',
  imports: [
    FormsModule, RouterLink, ButtonModule, CardModule, InputNumberModule, InputTextModule,
    DateField, MaintenanceDialog, MoneyField, MonthNavigation
  ],
  templateUrl: './entries-page.html',
  styleUrl: './entries-page.css'
})
export class EntriesPage {
  readonly store = inject(FinanceStore);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;
  readonly necessityLabel = necessityLabel;
  readonly categoryRows = categoryRows;
  readonly numberIsInteger = Number.isInteger;
  attempted = false;

  changeMonth(month: string): void {
    this.store.selectedMonth = month;
    void this.store.loadEntries();
  }

  occurrencesLabel(): string {
    const count = this.store.selectedSeries().length;
    return count === 1 ? '1 ocorrência gerada' : `${count} ocorrências geradas`;
  }

  async submit(): Promise<void> {
    this.attempted = true;
    await this.store.createEntry();
    if (this.store.notice()) this.attempted = false;
    if (this.store.entryPaymentMethod === 'wallet' && this.store.selectedSeriesId() !== null) this.focusSeries();
  }

  paymentChanged(): void {
    if (this.store.entryPaymentMethod === 'card') {
      if (!this.store.entryCategoryIsExpense()) this.store.entryCategoryId = null;
    }
    this.store.error.set('');
    this.store.notice.set('');
  }

  async openSeries(id: number): Promise<void> {
    await this.store.showSeries(id);
    this.focusSeries();
  }

  startEdit(entry: Entry): void {
    this.store.startEditEntry(entry);
    setTimeout(() => this.scrollTo('entry-edit'), 0);
  }

  editDisabled(): boolean {
    return this.store.editWalletId === null || this.store.editCategoryId === null
      || this.store.editAmount === null
      || !this.store.editExpectedOn
      || (this.store.editCategoryIsExpense() && !this.store.editingEntryUnclassified
        && this.store.editNecessity === null);
  }

  editedEntryForEntity(): Entry | undefined {
    const id = this.store.editingEntryEntityId;
    return id === null ? undefined : this.store.entries().find(entry => entry.id === id);
  }

  entryEntityChoices(entry: Entry): Counterparty[] {
    return this.store.counterparties().filter(entity =>
      entity.active || entity.id === entry.entityId);
  }

  readonly entryEntityDirty = (): boolean => {
    const entry = this.editedEntryForEntity();
    return !!entry && this.store.entryEntitySelection !== entry.entityId;
  };

  openEntryEntityEditor(entry: Entry): void {
    this.store.startEntryEntityEdit(entry);
  }

  closeEntryEntityEditor(): void {
    this.store.editingEntryEntityId = null;
  }

  async saveEntryEntity(): Promise<void> {
    await this.store.saveEntryEntity();
  }

  private focusSeries(): void {
    this.scrollTo('series-details');
  }

  private scrollTo(id: string): void {
    const element = document.getElementById(id);
    if (element && typeof element.scrollIntoView === 'function') {
      element.scrollIntoView({ block: 'start' });
    }
  }
}
