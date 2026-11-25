import { afterNextRender, Component, inject, Injector, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { Counterparty, Entry } from '../api';
import { money, monthLabel, necessityLabel, shortDate } from '../format';
import { DateField } from '../date-field';
import { MaintenanceDialog } from '../maintenance-dialog';
import { MoneyField } from '../money-field';
import { MonthNavigation } from '../month-navigation';
import { ActionConfirmation } from '../action-confirmation';

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
  private readonly confirmation = inject(ActionConfirmation);
  private readonly injector = inject(Injector);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;
  readonly necessityLabel = necessityLabel;
  readonly numberIsInteger = Number.isInteger;
  attempted = false;
  selectedEntryId: number | null = null;
  readonly detailMode = signal<'view' | 'edit' | 'entity' | 'series'>('view');
  readonly modalSeries = signal<Entry[]>([]);
  modalSeriesId: number | null = null;
  seriesOriginEntryId: number | null = null;
  readonly seriesLoading = signal(false);
  entryActionNotice = '';

  changeMonth(month: string): void {
    this.closeEntry();
    this.entryActionNotice = '';
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

  selectedEntry(): Entry | undefined {
    return this.store.entries().find(entry => entry.id === this.selectedEntryId)
      ?? this.modalSeries().find(entry => entry.id === this.selectedEntryId)
      ?? this.store.selectedSeries().find(entry => entry.id === this.selectedEntryId);
  }

  openEntry(entry: Entry, trigger?: EventTarget | null): void {
    if (trigger instanceof HTMLElement) trigger.focus();
    this.store.error.set('');
    this.store.notice.set('');
    this.entryActionNotice = '';
    this.selectedEntryId = entry.id;
    this.detailMode.set('view');
    if (this.modalSeriesId === entry.seriesId) this.focusModal('.entry-back-to-series');
  }

  closeEntry(): void {
    this.store.cancelEntryEdit();
    this.store.editingEntryEntityId = null;
    this.selectedEntryId = null;
    this.detailMode.set('view');
    this.modalSeriesId = null;
    this.seriesOriginEntryId = null;
    this.modalSeries.set([]);
  }

  readonly entryDetailsDirty = (): boolean => {
    const entry = this.selectedEntry();
    if (!entry) return false;
    if (this.detailMode() === 'entity') return this.entryEntityDirty();
    if (this.detailMode() !== 'edit') return false;
    return this.store.editWalletId !== entry.walletId
      || this.store.editCategoryId !== entry.categoryId
      || this.store.editNecessity !== entry.necessity
      || this.store.editEntityId !== entry.entityId
      || this.store.editDescription !== entry.description
      || this.store.editAmount !== entry.amount
      || this.store.editExpectedOn !== (entry.occurredOn ?? entry.expectedOn);
  };

  modalOccurrencesLabel(): string {
    const count = this.modalSeries().length;
    return count === 1 ? '1 ocorrência gerada' : `${count} ocorrências geradas`;
  }

  backToSeries(): void {
    this.store.error.set('');
    this.detailMode.set('series');
    this.focusModal(`.modal-series-list [data-entry-id="${this.selectedEntryId}"]`);
  }

  backToOriginalEntry(): void {
    this.selectedEntryId = this.seriesOriginEntryId;
    this.detailMode.set('view');
    this.focusModal('.entry-back-to-series');
  }

  async backToDetails(): Promise<void> {
    if (this.store.busy()) return;
    if (this.entryDetailsDirty() && !await this.confirmation.ask('Descartar alterações',
      'Descartar as alterações não salvas?', 'Descartar')) return;
    this.store.cancelEntryEdit();
    this.store.editingEntryEntityId = null;
    this.store.error.set('');
    this.detailMode.set('view');
  }

  startEdit(entry: Entry): void {
    this.store.startEditEntry(entry);
    if (this.store.editingEntryId() === entry.id) this.detailMode.set('edit');
  }

  async saveEdit(scope: 'single' | 'future'): Promise<void> {
    await this.store.saveEntryEdit(scope);
    if (this.store.editingEntryId() === null) {
      this.detailMode.set('view');
      if (!this.selectedEntry()) {
        this.entryActionNotice = 'Lançamento atualizado.';
        this.closeEntry();
      }
    }
  }

  async cancelSelectedEntry(entry: Entry): Promise<void> {
    await this.store.cancelEntry(entry);
    if (this.store.notice() === 'Lançamento cancelado.') {
      this.entryActionNotice = 'Lançamento cancelado.';
      this.closeEntry();
    }
  }

  async openEntrySeries(id: number): Promise<void> {
    if (this.seriesLoading()) return;
    const originEntryId = this.selectedEntryId;
    this.seriesLoading.set(true);
    try {
      const occurrences = await this.store.fetchSeries(id);
      if (occurrences && this.selectedEntryId === originEntryId && originEntryId !== null) {
        this.seriesOriginEntryId = originEntryId;
        this.modalSeriesId = id;
        this.modalSeries.set(occurrences);
        this.detailMode.set('series');
        this.focusModal('.modal-series-list .entry-row-trigger');
      }
    } finally {
      this.seriesLoading.set(false);
    }
  }

  editDisabled(): boolean {
    return this.store.editWalletId === null || this.store.editCategoryId === null
      || this.store.editAmount === null
      || !this.store.editExpectedOn
      || (this.store.editCategoryIsExpense() && !this.store.editingEntryUnclassified
        && this.store.editNecessity === null);
  }

  entryEntityChoices(entry: Entry): Counterparty[] {
    return this.store.counterparties().filter(entity =>
      entity.active || entity.id === entry.entityId);
  }

  readonly entryEntityDirty = (): boolean => {
    const entry = this.selectedEntry();
    return !!entry && this.store.entryEntitySelection !== entry.entityId;
  };

  openEntryEntityEditor(entry: Entry): void {
    this.store.startEntryEntityEdit(entry);
    if (this.store.editingEntryEntityId === entry.id) this.detailMode.set('entity');
  }

  async saveEntryEntity(): Promise<void> {
    await this.store.saveEntryEntity();
    if (this.store.editingEntryEntityId === null) this.detailMode.set('view');
  }

  private focusSeries(): void {
    this.scrollTo('series-details');
  }

  private focusModal(selector: string): void {
    afterNextRender(() => document.querySelector<HTMLElement>(`.p-dialog ${selector}`)?.focus(),
      { injector: this.injector });
  }

  private scrollTo(id: string): void {
    const element = document.getElementById(id);
    if (element && typeof element.scrollIntoView === 'function') {
      element.scrollIntoView({ block: 'start' });
    }
  }
}
