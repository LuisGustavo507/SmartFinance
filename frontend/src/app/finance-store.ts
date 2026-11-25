import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ActionConfirmation } from './action-confirmation';
import { money } from './format';
import { Api, CardInstallment, CardPurchase, CardStatement, Category, CategoryKind, Counterparty, CreditCard, Entry, EntityPeriodReport, Institution, MonthlyPoint, MonthlySummary, Necessity, Profile, ReversalCredit, Transfer, Wallet, WalletType } from './api';

@Injectable({ providedIn: 'root' })
export class FinanceStore {
  private readonly api = inject(Api);
  private readonly confirmation = inject(ActionConfirmation);
  readonly profile = signal<Profile | null>(null);
  readonly wallets = signal<Wallet[]>([]);
  readonly institutions = signal<Institution[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly counterparties = signal<Counterparty[]>([]);
  readonly entries = signal<Entry[]>([]);
  readonly selectedSeries = signal<Entry[]>([]);
  readonly selectedSeriesId = signal<number | null>(null);
  readonly editingEntryId = signal<number | null>(null);
  readonly monthlySummary = signal<MonthlySummary | null>(null);
  readonly entityPeriodReport = signal<EntityPeriodReport | null>(null);
  readonly monthlyPoints = signal<MonthlyPoint[]>([]);
  readonly reportLoading = signal(false);
  readonly reportLoadFailed = signal(false);
  readonly transfers = signal<Transfer[]>([]);
  readonly cards = signal<CreditCard[]>([]);
  readonly cardStatements = signal<CardStatement[]>([]);
  readonly cardCredits = signal<ReversalCredit[]>([]);
  readonly entriesLoading = signal(false);
  readonly entriesLoadFailed = signal(false);
  readonly cardsLoading = signal(false);
  readonly cardsLoadFailed = signal(false);
  readonly transfersLoading = signal(false);
  readonly transfersLoadFailed = signal(false);
  private entriesRequest = 0;
  private cardsRequest = 0;
  private transfersRequest = 0;
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly ready = signal(false);
  readonly activeWallets = computed(() => this.wallets().filter(wallet => wallet.active));
  readonly expenseCategories = computed(() => this.categories()
    .filter(category => category.active && category.kind === 'DESPESA'));
  readonly totalBalance = computed(() =>
    this.activeWallets().reduce((total, wallet) => total + wallet.estimatedBalance, 0));
  mode: 'login' | 'register' = 'login';
  name = '';
  email = '';
  password = '';
  walletName = '';
  walletType: WalletType = 'CARTEIRA_DINHEIRO';
  walletInstitutionChoice = '';
  walletInstitutionName = '';

  get walletInstitution(): string {
    return this.walletInstitutionChoice === 'OUTRA'
      ? this.walletInstitutionName.trim()
      : this.walletInstitutionChoice;
  }
  categoryName = '';
  editingCategoryId: number | null = null;
  editingCategoryName = '';
  editingCategoryKind: CategoryKind = 'DESPESA';
  counterpartyName = '';
  editingEntityId: number | null = null;
  editingEntityName = '';
  categoryKind: CategoryKind = 'DESPESA';
  entryWalletId: number | null = null;
  entryCategoryId: number | null = null;
  entryNecessity: Necessity | null = null;
  entryEntityId: number | null = null;
  entryNewEntityName = '';
  filterEntityId: number | null = null;
  reportFromMonth = new Date().toISOString().slice(0, 7);
  reportToMonth = new Date().toISOString().slice(0, 7);
  entryDescription = '';
  entryAmount: number | null = null;
  entryDate = new Date().toISOString().slice(0, 10);
  entryPaymentMethod: 'wallet' | 'card' = 'wallet';
  entryMode: 'realized' | 'planned' | 'series' = 'realized';
  seriesCount: number | null = 3;
  editingSeriesId: number | null = null;
  editingOccurrenceNumber: number | null = null;
  editWalletId: number | null = null;
  editCategoryId: number | null = null;
  editNecessity: Necessity | null = null;
  editingEntryEntityId: number | null = null;
  entryEntitySelection: number | null = null;
  editingPurchaseEntityId: number | null = null;
  purchaseEntitySelection: number | null = null;
  editingEntryKind: CategoryKind | null = null;
  editingEntryStatus: 'PREVISTO' | 'EFETIVADO' | null = null;
  editingEntryUnclassified = false;
  editEntityId: number | null = null;
  editDescription = '';
  editAmount: number | null = null;
  editExpectedOn = '';
  currentBusinessDate(): string {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(new Date());
    const part = (type: string) => parts.find(item => item.type === type)!.value;
    return `${part('year')}-${part('month')}-${part('day')}`;
  }

  entryCanChange(entry: Entry): boolean {
    return entry.status !== 'CANCELADO'
      && entry.referenceMonth.slice(0, 7) >= this.currentBusinessDate().slice(0, 7);
  }
  readonly minimumExpectedDate = (() => {
    const date = new Date();
    date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10);
  })();
  selectedMonth = this.entryDate.slice(0, 7);
  initialBalance: number | null = 0;
  initialBalanceDate = new Date().toISOString().slice(0, 10);
  transferSourceId: number | null = null;
  transferDestinationId: number | null = null;
  transferAmount: number | null = null;
  transferDescription = '';
  transferDateTime = new Date().toISOString().slice(0, 16);
  transferMonth = this.transferDateTime.slice(0, 7);
  cardName = '';
  cardInstitutionChoice = '';
  cardInstitutionName = '';
  cardClosingDay: number | null = 28;
  cardDueDay: number | null = 5;
  cardLimit: number | null = null;
  cardDefaultWalletId: number | null = null;
  editingCardId: number | null = null;
  editingCardName = '';
  editingCardInstitutionChoice = '';
  editingCardInstitutionName = '';
  editingClosingDay: number | null = null;
  editingDueDay: number | null = null;
  editingCardLimit: number | null = null;
  editingCardWalletId: number | null = null;

  get cardInstitution(): string {
    return this.cardInstitutionChoice === 'OUTRA'
      ? this.cardInstitutionName.trim()
      : this.cardInstitutionChoice;
  }

  get editingCardInstitution(): string {
    return this.editingCardInstitutionChoice === 'OUTRA'
      ? this.editingCardInstitutionName.trim()
      : this.editingCardInstitutionChoice;
  }

  purchaseCardId: number | null = null;
  purchaseCategoryId: number | null = null;
  purchaseEntityId: number | null = null;
  purchaseNewEntityName = '';
  purchaseDescription = '';
  purchaseAmount: number | null = null;
  purchaseDate = new Date().toISOString().slice(0, 10);
  purchaseInstallments: number | null = 1;
  purchaseAmountMode: 'total' | 'installment' = 'total';
  purchaseNecessity: Necessity = 'NECESSARIA';
  statementMonth = this.selectedMonth;
  statementCardId: number | null = null;
  paymentStatementId: number | null = null;
  paymentWalletId: number | null = null;
  paymentDateTime = new Date().toISOString().slice(0, 16);
  private initialization: Promise<void> | null = null;

  initialize(): Promise<void> {
    this.initialization ??= this.loadSession();
    return this.initialization;
  }

  private async loadSession(): Promise<void> {
    try {
      await firstValueFrom(this.api.csrf());
      try {
        await this.loadProfile();
      } catch (error) {
        // A missing session is normal on a fresh visit.
        if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
          this.error.set('Não foi possível recuperar a sessão. Tente recarregar a página.');
        }
      }
    } catch {
      this.error.set('Não foi possível conectar à API. Confira se o backend está ligado.');
    } finally {
      this.ready.set(true);
    }
  }

  switchMode(mode: 'login' | 'register'): void {
    this.mode = mode;
    this.error.set('');
    this.notice.set('');
  }

  async submitAccount(): Promise<void> {
    if (this.busy()) return;
    this.error.set('');
    this.notice.set('');
    this.busy.set(true);
    try {
      // Spring may clear the CSRF cookie after a failed request or login.
      await firstValueFrom(this.api.csrf());
      if (this.mode === 'register') {
        await firstValueFrom(this.api.register(this.name, this.email, this.password));
        this.mode = 'login';
        this.notice.set('Cadastro concluído. Entre com sua senha.');
      } else {
        await firstValueFrom(this.api.login(this.email, this.password));
        await firstValueFrom(this.api.csrf());
        await this.loadProfile();
        this.password = '';
      }
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401 && this.mode === 'login') {
        this.error.set('Email ou senha inválidos. Confira os dados e tente novamente.');
      } else if (error instanceof HttpErrorResponse && error.status === 409 && this.mode === 'register') {
        this.error.set('Este email já está cadastrado.');
      } else if (error instanceof HttpErrorResponse && error.status === 403
          && this.mode === 'register' && error.error?.code === 'invite_required') {
        this.error.set('Este email não está na lista de convidados do beta.');
      } else {
        this.error.set(this.message(error, this.mode === 'register'
          ? 'Não foi possível cadastrar.' : 'Não foi possível entrar. Tente novamente.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async createWallet(): Promise<void> {
    this.error.set('');
    if (this.initialBalance === null || !this.initialBalanceDate) {
      this.error.set('Informe o saldo e a data inicial.');
      return;
    }
    if (this.walletType !== 'CARTEIRA_DINHEIRO' && !this.walletInstitution.trim()) {
      this.error.set('Informe a instituição da conta.');
      return;
    }
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.createWallet(this.walletName, this.initialBalance,
        this.initialBalanceDate, this.walletType,
        this.walletType === 'CARTEIRA_DINHEIRO' ? null : this.walletInstitution));
      this.walletName = '';
      this.walletInstitutionChoice = '';
      this.walletInstitutionName = '';
      this.initialBalance = 0;
      this.wallets.set(await firstValueFrom(this.api.wallets()));
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível criar a carteira.'));
    } finally {
      this.busy.set(false);
    }
  }

  async createCategory(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.createCategory(this.categoryName, this.categoryKind));
      this.categoryName = '';
      this.categories.set(await firstValueFrom(this.api.categories()));
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível criar a categoria.'));
    } finally {
      this.busy.set(false);
    }
  }

  startCategoryEdit(category: Category): void {
    this.error.set('');
    this.editingCategoryId = category.id;
    this.editingCategoryName = category.name;
    this.editingCategoryKind = category.kind;
  }

  async saveCategory(): Promise<void> {
    const id = this.editingCategoryId;
    if (id === null || !this.editingCategoryName.trim() || this.busy()) return;
    this.error.set('');
    this.notice.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      const updated = await firstValueFrom(this.api.updateCategory(id,
        this.editingCategoryName.trim(), this.editingCategoryKind));
      this.categories.update(items => items.map(item => item.id === id ? updated : item));
      this.editingCategoryId = null;
      this.notice.set('Categoria atualizada.');
      await this.loadEntries();
      if (this.statementMonth) await this.loadCards();
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Nome já usado nesta natureza ou categoria vinculada: altere apenas o nome.');
      } else {
        this.error.set(this.message(error, 'Não foi possível editar a categoria.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async deleteCategory(category: Category): Promise<void> {
    if (this.busy()) return;
    if (!await this.confirmation.ask('Excluir categoria',
      `Excluir a categoria “${category.name}” definitivamente? Só é possível se não houver lançamentos nem compras vinculados.`,
      'Excluir categoria')) return;
    if (this.busy()) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.deleteCategory(category.id));
      this.categories.update(items => items.filter(item => item.id !== category.id));
      if (this.editingCategoryId === category.id) this.editingCategoryId = null;
      if (this.entryCategoryId === category.id) this.entryCategoryId = null;
      if (this.purchaseCategoryId === category.id) this.purchaseCategoryId = null;
      this.notice.set('Categoria excluída.');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Esta categoria está vinculada a um lançamento ou compra. Você pode desativá-la, mas não excluí-la.');
      } else {
        this.error.set(this.message(error, 'Não foi possível excluir a categoria.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async setCategoryActive(category: Category, active: boolean): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      const updated = await firstValueFrom(this.api.setCategoryActive(category.id, active));
      this.categories.update(categories =>
        categories.map(item => item.id === category.id ? updated : item));
      if (!active && this.entryCategoryId === category.id) this.entryCategoryId = null;
      if (!active && this.purchaseCategoryId === category.id) this.purchaseCategoryId = null;
      this.notice.set(active ? 'Categoria reativada.' : 'Categoria desativada.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível alterar a categoria.'));
    } finally {
      this.busy.set(false);
    }
  }

  async createCounterparty(): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.createCounterparty(this.counterpartyName));
      this.counterpartyName = '';
      this.counterparties.set(await firstValueFrom(this.api.counterparties()));
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível cadastrar a entidade.'));
    } finally {
      this.busy.set(false);
    }
  }

  async setCounterpartyActive(id: number, active: boolean): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      const updated = await firstValueFrom(this.api.setCounterpartyActive(id, active));
      this.counterparties.update(entities =>
        entities.map(entity => entity.id === id ? updated : entity));
      if (!active && this.entryEntityId === id) this.entryEntityId = null;
      this.notice.set(active ? 'Entidade reativada.' : 'Entidade desativada.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível alterar a entidade.'));
    } finally {
      this.busy.set(false);
    }
  }

  startRenameEntity(entity: Counterparty): void {
    this.editingEntityId = entity.id;
    this.editingEntityName = entity.name;
  }

  async renameCounterparty(): Promise<void> {
    const id = this.editingEntityId;
    if (id === null || !this.editingEntityName.trim()) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      const updated = await firstValueFrom(this.api.renameCounterparty(id, this.editingEntityName));
      this.counterparties.update(entities =>
        entities.map(entity => entity.id === id ? updated : entity));
      this.entries.update(entries =>
        entries.map(entry => entry.entityId === id ? { ...entry, entityName: updated.name } : entry));
      this.editingEntityId = null;
      this.editingEntityName = '';
      this.notice.set('Entidade atualizada.');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Já existe uma entidade com esse nome.');
      } else {
        this.error.set(this.message(error, 'Não foi possível renomear a entidade.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async deleteCounterparty(id: number): Promise<void> {
    if (this.busy()) return;
    const entity = this.counterparties().find(item => item.id === id);
    if (!await this.confirmation.ask('Excluir entidade',
      `Excluir a entidade “${entity?.name ?? 'selecionada'}” sem vínculos? Esta ação não pode ser desfeita.`,
      'Excluir entidade')) return;
    if (this.busy()) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.deleteCounterparty(id));
      this.counterparties.update(entities => entities.filter(entity => entity.id !== id));
      if (this.entryEntityId === id) this.entryEntityId = null;
      if (this.filterEntityId === id) {
        this.filterEntityId = null;
        await this.loadEntries();
      }
      if (this.editingEntityId === id) this.editingEntityId = null;
      this.notice.set('Entidade excluída.');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Entidade vinculada a lançamentos não pode ser excluída.');
      } else {
        this.error.set(this.message(error, 'Não foi possível excluir a entidade.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async createEntry(): Promise<void> {
    if (this.busy()) return;
    if (this.entryPaymentMethod === 'card') {
      this.error.set('');
      this.notice.set('');
      if (this.purchaseCardId === null || !this.cards().some(card =>
        card.id === this.purchaseCardId && card.active)) {
        this.error.set('Selecione um cartão ativo.');
        return;
      }
      if (this.entryCategoryId === null || !this.entryCategoryIsExpense()
        || this.entryAmount === null || this.entryAmount <= 0 || !this.entryDate
        || this.entryNecessity === null) {
        this.error.set('Informe categoria de despesa, necessidade, valor e data da compra.');
        return;
      }
      if (this.purchaseInstallments === null || !Number.isInteger(this.purchaseInstallments)
        || this.purchaseInstallments < 1 || this.purchaseInstallments > 120) {
        this.error.set('Informe entre 1 e 120 parcelas.');
        return;
      }
      const purchaseTotal = this.cardPurchaseTotal();
      if (purchaseTotal === null) {
        this.error.set('O valor total calculado é alto demais para registrar a compra.');
        return;
      }
      this.purchaseCategoryId = this.entryCategoryId;
      this.purchaseNecessity = this.entryNecessity;
      this.purchaseDescription = this.entryDescription;
      this.purchaseAmount = purchaseTotal;
      this.purchaseDate = this.entryDate;
      this.purchaseEntityId = this.entryEntityId;
      this.purchaseNewEntityName = this.entryNewEntityName;
      await this.registerCardPurchase();
      if (this.notice() === 'Compra registrada no cartão.') {
        this.entryDescription = '';
        this.entryAmount = null;
        this.entryNewEntityName = '';
        this.notice.set(`Compra registrada. Primeira parcela em ${this.statementMonth}. Consulte a página Faturas.`);
      }
      return;
    }
    this.error.set('');
    this.notice.set('');
    if (this.entryCategoryIsExpense() && this.entryNecessity === null) {
      this.error.set('Classifique a necessidade da despesa.');
      return;
    }
    if (this.entryWalletId === null || this.entryCategoryId === null
      || this.entryAmount === null || this.entryAmount <= 0 || !this.entryDate) {
      this.error.set('Informe carteira, categoria, valor e data do fato.');
      return;
    }
    const wallet = this.wallets().find(item => item.id === this.entryWalletId);
    if (wallet && this.entryDate < wallet.initialBalanceDate) {
      this.error.set('Escolha uma data a partir do início do saldo da carteira.');
      return;
    }
    const today = new Date().toISOString().slice(0, 10);
    if (this.entryMode === 'realized' && this.entryDate > today) {
      this.error.set('Um lançamento que já ocorreu não pode ter data futura.');
      return;
    }
    if (this.entryMode !== 'realized' && this.entryDate <= today) {
      this.error.set('Para uma previsão, escolha uma data futura.');
      return;
    }
    if (this.entryMode === 'series' &&
      (this.seriesCount === null || !Number.isInteger(this.seriesCount)
        || this.seriesCount < 1 || this.seriesCount > 120)) {
      this.error.set('Informe entre 1 e 120 ocorrências mensais.');
      return;
    }
    this.busy.set(true);
    let created = false;
    try {
      await firstValueFrom(this.api.csrf());
      const entity = this.entryEntityPayload();
      if (this.entryMode === 'series') {
        const series = await firstValueFrom(this.api.createSeries(
          this.entryWalletId, this.entryCategoryId, this.entryDescription,
          this.entryAmount, this.entryDate, this.seriesCount!,
           entity.entityId, this.entryCategoryIsExpense() ? this.entryNecessity : null,
           entity.newEntityName));
        this.selectedSeries.set(series);
        this.selectedSeriesId.set(series[0].seriesId);
        created = true;
      } else if (this.entryMode === 'planned') {
        await firstValueFrom(this.api.planEntry(this.entryWalletId, this.entryCategoryId,
           this.entryDescription, this.entryAmount, this.entryDate, entity.entityId,
           this.entryCategoryIsExpense() ? this.entryNecessity : null,
            entity.newEntityName));
        created = true;
      } else {
        await firstValueFrom(this.api.createEntry(this.entryWalletId, this.entryCategoryId,
           this.entryDescription, this.entryAmount, this.entryDate, entity.entityId,
           this.entryCategoryIsExpense() ? this.entryNecessity : null,
            entity.newEntityName));
        created = true;
      }
      this.entryDescription = '';
      this.entryAmount = null;
      this.entryNecessity = null;
      this.entryNewEntityName = '';
      this.selectedMonth = this.entryDate.slice(0, 7);
      this.filterEntityId = null;
      await this.reloadMonth();
      this.notice.set(this.entryMode === 'series' ? 'Série criada. Confira as ocorrências abaixo.'
        : 'Lançamento registrado. Ele já aparece na lista do mês.');
    } catch (error) {
      if (created) {
        this.notice.set('Lançamento registrado.');
        this.error.set('O lançamento foi salvo, mas não foi possível atualizar a lista. Recarregue a página.');
      } else {
        this.error.set(this.message(error, 'Não foi possível registrar o lançamento.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  cardPurchaseTotal(): number | null {
    if (this.entryAmount === null || !Number.isFinite(this.entryAmount)
      || this.entryAmount <= 0 || this.purchaseInstallments === null
      || !Number.isInteger(this.purchaseInstallments)
      || this.purchaseInstallments < 1 || this.purchaseInstallments > 120) return null;
    const cents = Math.round(this.entryAmount * 100);
    const totalCents = this.purchaseAmountMode === 'installment'
      ? cents * this.purchaseInstallments : cents;
    return Number.isSafeInteger(totalCents) && totalCents > 0 ? totalCents / 100 : null;
  }

  onEntryModeChange(): void {
    const today = new Date().toISOString().slice(0, 10);
    if (this.entryMode !== 'realized' && this.entryDate <= today) {
      const tomorrow = new Date();
      tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
      this.entryDate = tomorrow.toISOString().slice(0, 10);
    }
  }

  entryCategoryIsExpense(): boolean {
    return this.categories().find(category => category.id === this.entryCategoryId)?.kind === 'DESPESA';
  }

  entryEntityPayload(): { entityId: number | null; newEntityName: string | null } {
    if (this.entryEntityId !== null) {
      return { entityId: this.entryEntityId, newEntityName: null };
    }
    const name = this.entryNewEntityName.trim();
    return { entityId: null, newEntityName: name ? name : null };
  }

  purchaseEntityPayload(): { entityId: number | null; newEntityName: string | null } {
    if (this.purchaseEntityId !== null) {
      return { entityId: this.purchaseEntityId, newEntityName: null };
    }
    const name = this.purchaseNewEntityName.trim();
    return { entityId: null, newEntityName: name ? name : null };
  }

  editCategoryIsExpense(): boolean {
    return this.categories().find(category => category.id === this.editCategoryId)?.kind === 'DESPESA';
  }

  startEditEntry(entry: Entry): void {
    if (!this.entryCanChange(entry)) return;
    this.error.set('');
    this.notice.set('');
    this.editingEntryId.set(entry.id);
    this.editingSeriesId = entry.seriesId;
    this.editingOccurrenceNumber = entry.occurrenceNumber;
    this.editWalletId = entry.walletId;
    this.editCategoryId = entry.categoryId;
    this.editNecessity = entry.necessity ?? null;
    this.editingEntryKind = entry.kind;
    this.editingEntryStatus = entry.status as 'PREVISTO' | 'EFETIVADO';
    this.editingEntryUnclassified = entry.kind === 'DESPESA' && entry.necessity == null;
    this.editEntityId = entry.entityId;
    this.editDescription = entry.description;
    this.editAmount = entry.amount;
    this.editExpectedOn = entry.occurredOn ?? entry.expectedOn ?? '';
  }

  cancelEntryEdit(): void {
    this.clearEntryEdit();
  }

  async saveEntryEdit(scope: 'single' | 'future'): Promise<void> {
    const id = this.editingEntryId();
    if (id === null) return;
    this.error.set('');
    this.notice.set('');
    if (this.editWalletId === null || this.editCategoryId === null
      || this.editAmount === null || !this.editExpectedOn) {
      this.error.set('Informe carteira, categoria, valor e data prevista.');
      return;
    }
    if (this.editCategoryIsExpense() && this.editNecessity === null
      && this.editingEntryKind === 'RECEITA') {
      this.error.set('Classifique a necessidade da despesa.');
      return;
    }
    if (this.editingEntryStatus === 'EFETIVADO'
      && (this.editExpectedOn > this.currentBusinessDate()
        || this.editExpectedOn.slice(0, 7) < this.currentBusinessDate().slice(0, 7))) {
      this.error.set('A data efetiva deve estar entre o início do mês atual e hoje.');
      return;
    }
    const future = scope === 'future';
    if (future && (this.editingEntryStatus !== 'PREVISTO'
      || this.editingSeriesId === null || this.editingOccurrenceNumber === null)) {
      this.error.set('Esta ocorrência não pertence a uma série.');
      return;
    }
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      if (future) {
        await firstValueFrom(this.api.updateSeriesFrom(this.editingSeriesId!,
          this.editingOccurrenceNumber!, this.editWalletId, this.editCategoryId,
           this.editDescription, this.editAmount, this.editExpectedOn, this.editEntityId,
           this.editCategoryIsExpense() ? this.editNecessity : null));
        this.notice.set('Ocorrência atual e futuras atualizadas.');
      } else {
        await firstValueFrom(this.api.updateEntry(id, this.editWalletId,
          this.editCategoryId, this.editDescription, this.editAmount,
          this.editExpectedOn, this.editingEntryStatus!, this.editEntityId,
          this.editCategoryIsExpense() ? this.editNecessity : null));
        this.notice.set('Lançamento atualizado.');
      }
      const wasRealized = this.editingEntryStatus === 'EFETIVADO';
      this.clearEntryEdit();
      await this.loadEntries();
      if (wasRealized) {
        this.wallets.set(await firstValueFrom(this.api.wallets()));
      }
      if (this.selectedSeriesId() !== null) {
        await this.showSeries(this.selectedSeriesId()!);
      }
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Este lançamento não pode mais ser editado: foi cancelado ou seu mês já terminou.');
      } else {
        this.error.set(this.message(error, 'Não foi possível editar o lançamento.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  private clearEntryEdit(): void {
    this.editingEntryId.set(null);
    this.editingSeriesId = null;
    this.editingOccurrenceNumber = null;
    this.editWalletId = null;
    this.editCategoryId = null;
    this.editNecessity = null;
    this.editingEntryKind = null;
    this.editingEntryStatus = null;
    this.editingEntryUnclassified = false;
    this.editEntityId = null;
    this.editDescription = '';
    this.editAmount = null;
    this.editExpectedOn = '';
  }

  startEntryEntityEdit(entry: Entry): void {
    if (!this.entryCanChange(entry)) return;
    this.error.set('');
    this.editingEntryEntityId = entry.id;
    this.entryEntitySelection = entry.entityId;
  }

  async saveEntryEntity(): Promise<void> {
    const id = this.editingEntryEntityId;
    if (id === null) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.updateEntryEntity(id, this.entryEntitySelection));
      this.editingEntryEntityId = null;
      this.notice.set('Entidade do lançamento atualizada.');
      await this.loadEntries();
      if (this.selectedSeriesId() !== null) {
        await this.showSeries(this.selectedSeriesId()!);
      }
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível atualizar a entidade.'));
    } finally {
      this.busy.set(false);
    }
  }

  startPurchaseEntityEdit(installment: CardInstallment): void {
    this.error.set('');
    this.editingPurchaseEntityId = installment.purchaseId;
    this.purchaseEntitySelection = installment.entityId;
  }

  async savePurchaseEntity(): Promise<void> {
    const id = this.editingPurchaseEntityId;
    if (id === null) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.updateCardPurchaseEntity(id, this.purchaseEntitySelection));
      this.editingPurchaseEntityId = null;
      this.notice.set('Entidade da compra atualizada.');
      await this.loadCards();
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível atualizar a entidade.'));
    } finally {
      this.busy.set(false);
    }
  }

  async cancelEntry(entry: Entry): Promise<void> {
    if (this.busy() || !this.entryCanChange(entry)) return;
    const detail = `${entry.description || 'Sem descrição'} · ${money(entry.amount)}`;
    const impact = entry.status === 'EFETIVADO'
      ? 'O valor deixará de compor o saldo da carteira e os resultados do mês.'
      : 'A previsão deixará de compor o resultado projetado.';
    if (!await this.confirmation.ask('Cancelar lançamento',
      `Cancelar “${detail}”? ${impact} O registro ficará guardado como cancelado.`,
      'Cancelar lançamento')) return;
    if (this.busy()) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.cancelEntry(entry.id));
      if (this.editingEntryId() === entry.id) this.clearEntryEdit();
      await this.loadEntries();
      if (entry.status === 'EFETIVADO') {
        this.wallets.set(await firstValueFrom(this.api.wallets()));
      }
      this.notice.set('Lançamento cancelado.');
      if (this.selectedSeriesId() !== null) {
        await this.showSeries(this.selectedSeriesId()!);
      }
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Este lançamento já foi cancelado ou seu mês já terminou.');
      } else {
        this.error.set(this.message(error, 'Não foi possível cancelar o lançamento.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async showSeries(id: number): Promise<void> {
    const occurrences = await this.fetchSeries(id);
    if (occurrences) {
      this.selectedSeriesId.set(id);
      this.selectedSeries.set(occurrences);
    }
  }

  async fetchSeries(id: number): Promise<Entry[] | null> {
    this.error.set('');
    try {
      return await firstValueFrom(this.api.series(id));
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível consultar a série.'));
      return null;
    }
  }

  closeSeries(): void {
    this.selectedSeriesId.set(null);
    this.selectedSeries.set([]);
  }

  async loadEntries(): Promise<void> {
    this.error.set('');
    const requestId = ++this.entriesRequest;
    this.entriesLoading.set(true);
    this.entriesLoadFailed.set(false);
    try {
      const month = this.selectedMonth;
      const entityId = this.filterEntityId;
      const [entries, summary] = await Promise.all([
        firstValueFrom(this.api.entries(month, entityId)),
        firstValueFrom(this.api.monthlySummary(month, entityId))
      ]);
      if (requestId === this.entriesRequest) {
        this.entries.set(entries);
        this.monthlySummary.set(summary);
      }
    } catch (error) {
      if (requestId === this.entriesRequest) {
        this.entriesLoadFailed.set(true);
        this.error.set(this.message(error, 'Não foi possível consultar os lançamentos.'));
      }
    } finally {
      if (requestId === this.entriesRequest) this.entriesLoading.set(false);
    }
  }

  async loadEntityPeriodReport(): Promise<void> {
    if (this.busy()) return;
    this.error.set('');
    if (!this.reportFromMonth || !this.reportToMonth) {
      this.error.set('Informe o início e o fim do período.');
      return;
    }
    if (this.reportFromMonth > this.reportToMonth) {
      this.error.set('O início do período deve vir antes do fim.');
      return;
    }
    this.busy.set(true);
    this.reportLoading.set(true);
    this.reportLoadFailed.set(false);
    this.entityPeriodReport.set(null);
    this.monthlyPoints.set([]);
    try {
      const [report, points] = await Promise.all([
        firstValueFrom(this.api.entityPeriodReport(this.reportFromMonth, this.reportToMonth)),
        firstValueFrom(this.api.monthlyReport(this.reportFromMonth, this.reportToMonth))
      ]);
      this.entityPeriodReport.set(report);
      this.monthlyPoints.set(points);
    } catch (error) {
      this.reportLoadFailed.set(true);
      this.error.set(this.message(error, 'Não foi possível gerar o relatório.'));
    } finally {
      this.busy.set(false);
      this.reportLoading.set(false);
    }
  }

  async createTransfer(): Promise<void> {
    this.error.set('');
    if (this.transferSourceId === null || this.transferDestinationId === null
      || this.transferSourceId === this.transferDestinationId
      || this.transferAmount === null || !this.transferDateTime) {
      this.error.set('Informe duas carteiras diferentes, valor e data da transferência.');
      return;
    }
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.createTransfer(this.transferSourceId, this.transferDestinationId,
        this.transferAmount, this.transferDescription, `${this.transferDateTime}:00Z`));
      this.transferAmount = null;
      this.transferDescription = '';
      this.transferMonth = this.transferDateTime.slice(0, 7);
      const [wallets, transfers] = await Promise.all([
        firstValueFrom(this.api.wallets()), firstValueFrom(this.api.transfers(this.transferMonth))
      ]);
      this.wallets.set(wallets);
      this.transfers.set(transfers);
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível registrar a transferência.'));
    } finally {
      this.busy.set(false);
    }
  }

  async loadTransfers(): Promise<void> {
    this.error.set('');
    const requestId = ++this.transfersRequest;
    this.transfersLoading.set(true);
    this.transfersLoadFailed.set(false);
    try {
      const month = this.transferMonth;
      const transfers = await firstValueFrom(this.api.transfers(month));
      if (requestId === this.transfersRequest) this.transfers.set(transfers);
    } catch (error) {
      if (requestId === this.transfersRequest) {
        this.transfersLoadFailed.set(true);
        this.error.set(this.message(error, 'Não foi possível consultar as transferências.'));
      }
    } finally {
      if (requestId === this.transfersRequest) this.transfersLoading.set(false);
    }
  }

  transferWalletName(id: number): string {
    return this.wallets().find(wallet => wallet.id === id)?.name ?? `Carteira ${id}`;
  }

  cardLabel(cardId: number): string {
    return this.cards().find(card => card.id === cardId)?.name ?? `Cartão ${cardId}`;
  }

  beginCardEdit(card: CreditCard): void {
    this.editingCardId = card.id;
    this.editingCardName = card.name;
    const institution = card.institutionName ?? '';
    const known = this.institutions().some(candidate =>
      candidate.code !== 'OUTRA' && candidate.name === institution);
    this.editingCardInstitutionChoice = known ? institution : 'OUTRA';
    this.editingCardInstitutionName = known ? '' : institution;
    this.editingClosingDay = card.closingDay;
    this.editingDueDay = card.dueDay;
    this.editingCardLimit = card.creditLimit;
    this.editingCardWalletId = card.defaultPaymentWalletId;
  }

  async updateCard(): Promise<void> {
    const id = this.editingCardId;
    if (id === null || !this.editingCardName.trim() || !this.editingCardInstitution.trim()
      || this.editingClosingDay === null || this.editingDueDay === null) {
      this.error.set('Preencha os dados obrigatórios do cartão.');
      return;
    }
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.updateCard(id, this.editingCardName,
        this.editingCardInstitution, this.editingClosingDay, this.editingDueDay,
        this.editingCardLimit, this.editingCardWalletId));
      this.editingCardId = null;
      this.editingCardInstitutionChoice = '';
      this.editingCardInstitutionName = '';
      this.cards.set(await firstValueFrom(this.api.cards()));
      this.notice.set('Cartão atualizado.');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Nome já usado ou dias do ciclo bloqueados após a primeira compra.');
      } else {
        this.error.set(this.message(error, 'Não foi possível atualizar o cartão.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async setCardActive(card: CreditCard, active: boolean): Promise<void> {
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      const updated = await firstValueFrom(this.api.setCardActive(card.id, active));
      this.cards.update(cards => cards.map(item => item.id === card.id ? updated : item));
      this.notice.set(active ? 'Cartão reativado.' : 'Cartão desativado.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível alterar o estado do cartão.'));
    } finally {
      this.busy.set(false);
    }
  }

  async payStatement(): Promise<void> {
    const statementId = this.paymentStatementId;
    this.error.set('');
    if (statementId === null || this.paymentWalletId === null || !this.paymentDateTime) {
      this.error.set('Informe a conta do pagamento e a data.');
      return;
    }
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.payCardStatement(statementId, this.paymentWalletId,
        `${this.paymentDateTime}:00Z`));
      this.notice.set('Fatura paga.');
      this.paymentStatementId = null;
      await this.loadCards();
      await this.refreshSummary();
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Esta fatura não pode ser paga neste estado.');
      } else {
        this.error.set(this.message(error, 'Não foi possível pagar a fatura.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async loadCardCatalog(): Promise<void> {
    this.error.set('');
    const requestId = ++this.cardsRequest;
    this.cardsLoading.set(true);
    this.cardsLoadFailed.set(false);
    try {
      const cards = await firstValueFrom(this.api.cards());
      if (requestId === this.cardsRequest) this.cards.set(cards);
    } catch (error) {
      if (requestId === this.cardsRequest) {
        this.cardsLoadFailed.set(true);
        this.error.set(this.message(error, 'Não foi possível carregar os cartões.'));
      }
    } finally {
      if (requestId === this.cardsRequest) this.cardsLoading.set(false);
    }
  }

  async loadCards(): Promise<void> {
    this.error.set('');
    const requestId = ++this.cardsRequest;
    this.cardsLoading.set(true);
    this.cardsLoadFailed.set(false);
    try {
      const month = this.statementMonth;
      const [cards, statements, credits] = await Promise.all([
        firstValueFrom(this.api.cards()),
        firstValueFrom(this.api.cardStatements(month)),
        firstValueFrom(this.api.cardCredits(month))
      ]);
      if (requestId === this.cardsRequest) {
        this.cards.set(cards);
        this.cardStatements.set(statements);
        this.cardCredits.set(credits);
      }
    } catch (error) {
      if (requestId === this.cardsRequest) {
        this.cardsLoadFailed.set(true);
        this.error.set(this.message(error, 'Não foi possível carregar as faturas.'));
      }
    } finally {
      if (requestId === this.cardsRequest) this.cardsLoading.set(false);
    }
  }

  async reverseCardPurchase(purchaseId: number): Promise<void> {
    if (this.busy()) return;
    if (!await this.confirmation.ask('Estornar compra',
      'Estornar a compra inteira? Parcelas de faturas abertas serão canceladas.',
      'Estornar compra')) return;
    if (this.busy()) return;
    this.error.set('');
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.reverseCardPurchase(purchaseId));
      this.notice.set('Compra estornada.');
      await this.loadCards();
      await this.refreshSummary();
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível estornar a compra.'));
    } finally {
      this.busy.set(false);
    }
  }

  async createCard(): Promise<void> {
    this.error.set('');
    if (!this.cardName.trim() || !this.cardInstitution.trim()
      || this.cardClosingDay === null || this.cardDueDay === null) {
      this.error.set('Informe nome, instituição, dia de fechamento e dia de vencimento.');
      return;
    }
    if (this.cardClosingDay < 1 || this.cardClosingDay > 28
      || this.cardDueDay < 1 || this.cardDueDay > 28) {
      this.error.set('Os dias de fechamento e vencimento devem ficar entre 1 e 28.');
      return;
    }
    this.busy.set(true);
    let created = false;
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.createCard(this.cardName, this.cardInstitution,
        this.cardClosingDay, this.cardDueDay, this.cardLimit, this.cardDefaultWalletId));
      created = true;
      this.cardName = '';
      this.cardInstitutionChoice = '';
      this.cardInstitutionName = '';
      this.cardLimit = null;
      this.notice.set('Cartão cadastrado.');
      this.cards.set(await firstValueFrom(this.api.cards()));
    } catch (error) {
      if (created) {
        this.error.set('Cartão cadastrado, mas não foi possível atualizar a lista. Recarregue a página.');
      } else if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('Já existe um cartão com esse nome.');
      } else {
        this.error.set(this.message(error, 'Não foi possível cadastrar o cartão.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async registerCardPurchase(): Promise<void> {
    if (this.busy()) return;
    this.error.set('');
    if (this.purchaseCardId === null || this.purchaseCategoryId === null
      || this.purchaseAmount === null || this.purchaseAmount <= 0 || !this.purchaseDate) {
      this.error.set('Informe cartão, categoria, valor e data da compra.');
      return;
    }
    if (this.purchaseInstallments === null || this.purchaseInstallments < 1
      || this.purchaseInstallments > 120 || !Number.isInteger(this.purchaseInstallments)) {
      this.error.set('Informe entre 1 e 120 parcelas.');
      return;
    }
    this.busy.set(true);
    let created = false;
    try {
      await firstValueFrom(this.api.csrf());
      const entity = this.purchaseEntityPayload();
      const purchase = await firstValueFrom(this.api.createCardPurchase(
        this.purchaseCardId, this.purchaseCategoryId, this.purchaseDescription,
        this.purchaseAmount, this.purchaseDate, this.purchaseInstallments,
        entity.entityId, this.purchaseNecessity, entity.newEntityName));
      created = true;
      this.purchaseDescription = '';
      this.purchaseAmount = null;
      this.purchaseInstallments = 1;
      this.purchaseNewEntityName = '';
      this.statementCardId = this.purchaseCardId;
      const first = purchase.installments[0];
      if (first) {
        this.statementMonth = first.statementReferenceMonth;
      }
      this.notice.set('Compra registrada no cartão.');
      await this.loadCards();
      await this.refreshSummary();
      if (this.cardsLoadFailed()) {
        this.error.set('A compra foi salva, mas não foi possível atualizar as faturas. Tente novamente na página Faturas.');
      }
    } catch (error) {
      if (created) {
        this.notice.set('Compra registrada no cartão.');
        this.error.set('A compra foi salva, mas não foi possível atualizar os dados da tela. Recarregue a página.');
      } else if (error instanceof HttpErrorResponse && error.status === 409) {
        this.error.set('O ciclo da fatura já foi encerrado ou o cartão está inativo.');
      } else {
        this.error.set(this.message(error, 'Não foi possível registrar a compra.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  async logout(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.logout());
      this.profile.set(null);
      this.wallets.set([]);
      this.categories.set([]);
      this.counterparties.set([]);
      this.entries.set([]);
      this.selectedSeries.set([]);
      this.selectedSeriesId.set(null);
      this.monthlySummary.set(null);
      this.transfers.set([]);
      this.cards.set([]);
      this.cardStatements.set([]);
      this.cardCredits.set([]);
      this.statementCardId = null;
      this.purchaseAmountMode = 'total';
      this.clearEntryEdit();
      this.notice.set('');
    } catch {
      this.error.set('Não foi possível encerrar a sessão. Tente novamente.');
    } finally {
      this.busy.set(false);
    }
  }

  private async reloadMonth(): Promise<void> {
    const [wallets, entries, summary] = await Promise.all([
      firstValueFrom(this.api.wallets()), firstValueFrom(this.api.entries(this.selectedMonth)),
      firstValueFrom(this.api.monthlySummary(this.selectedMonth))
    ]);
    this.wallets.set(wallets);
    this.entries.set(entries);
    this.monthlySummary.set(summary);
  }

  private async loadProfile(): Promise<void> {
    const profile = await firstValueFrom(this.api.me());
    const [wallets, categories, counterparties, entries, summary, transfers, cards,
      institutions] = await Promise.all([
      firstValueFrom(this.api.wallets()), firstValueFrom(this.api.categories()),
      firstValueFrom(this.api.counterparties()),
      firstValueFrom(this.api.entries(this.selectedMonth)),
      firstValueFrom(this.api.monthlySummary(this.selectedMonth)),
      firstValueFrom(this.api.transfers(this.transferMonth)),
      firstValueFrom(this.api.cards()),
      firstValueFrom(this.api.institutions())
    ]);
    this.wallets.set(wallets);
    this.categories.set(categories);
    this.counterparties.set(counterparties);
    this.entries.set(entries);
    this.monthlySummary.set(summary);
    this.transfers.set(transfers);
    this.cards.set(cards);
    this.institutions.set(institutions);
    this.profile.set(profile);
  }

  private async refreshSummary(): Promise<void> {
    this.monthlySummary.set(await firstValueFrom(this.api.monthlySummary(this.selectedMonth)));
  }

  private message(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 409) return 'Este email ou nome de carteira já está em uso.';
      if (error.status === 400) return 'Confira os dados informados.';
      if (error.status === 403) return 'Verificação de segurança falhou. Recarregue a página.';
      if (error.status === 401) {
        this.profile.set(null);
        this.wallets.set([]);
        return 'Sessão encerrada. Entre novamente.';
      }
    }
    return fallback;
  }
}
