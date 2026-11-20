import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { FinanceStore } from './finance-store';
import { Entry } from './api';
import { vi } from 'vitest';

const summary = (month: string, realizedExpenses = 0, plannedExpenses = 0) => ({
  month, realizedIncome: 0, realizedExpenses,
  realizedResult: realizedExpenses === 0 ? 0 : -realizedExpenses,
  plannedIncome: 0, plannedExpenses,
  projectedResult: realizedExpenses + plannedExpenses === 0
    ? 0 : -realizedExpenses - plannedExpenses
});

const profile = { id: 1, name: 'Ana', email: 'ana@example.test' };

describe('FinanceStore', () => {
  let store: FinanceStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(FinanceStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function authenticate(options: {
    wallets?: unknown[]; categories?: unknown[]; entities?: unknown[];
    entries?: unknown[]; summary?: unknown; transfers?: unknown[]; cards?: unknown[];
    institutions?: unknown[];
  } = {}): Promise<void> {
    const initialization = store.initialize();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/me').flush(profile);
    await Promise.resolve();
    http.expectOne('/api/wallets').flush(options.wallets ?? []);
    http.expectOne('/api/categories').flush(options.categories ?? []);
    http.expectOne('/api/entities').flush(options.entities ?? []);
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.has('month')).flush(options.entries ?? []);
    http.expectOne(request => request.url === '/api/monthly-summary' &&
      request.params.has('month')).flush(options.summary ?? summary('2026-11-01'));
    http.expectOne(request => request.url === '/api/transfers' &&
      request.params.has('month')).flush(options.transfers ?? []);
    http.expectOne('/api/cards').flush(options.cards ?? []);
    http.expectOne('/api/institutions').flush(options.institutions ?? []);
    await initialization;
  }

  it('obtains CSRF and stays logged out when there is no session', async () => {
    const initialization = store.initialize();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await initialization;
    expect(store.ready()).toBe(true);
    expect(store.profile()).toBeNull();
    expect(store.error()).toBe('');
  });

  it('explains invalid credentials without claiming a session ended', async () => {
    store.email = 'unknown@example.test';
    store.password = 'wrong';
    const submission = store.submitAccount();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/login').flush(null,
      { status: 401, statusText: 'Unauthorized' });
    await submission;
    expect(store.profile()).toBeNull();
    expect(store.error()).toContain('Email ou senha inválidos');
    expect(store.error()).not.toContain('Sessão encerrada');
    expect(store.email).toBe('unknown@example.test');
  });

  it('explains that registration requires an invited email', async () => {
    store.switchMode('register');
    store.name = 'Ana';
    store.email = 'ana@example.test';
    store.password = 'senha-de-teste-segura';
    const submission = store.submitAccount();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/register').flush(
      { code: 'invite_required' },
      { status: 403, statusText: 'Forbidden' });
    await submission;

    expect(store.mode).toBe('register');
    expect(store.error()).toContain('lista de convidados');
  });

  it('distinguishes loading, failure, and empty entries and allows retry', async () => {
    store.selectedMonth = '2026-12';
    const first = store.loadEntries();
    expect(store.entriesLoading()).toBe(true);
    http.expectOne(request => request.url === '/api/entries').flush(null,
      { status: 503, statusText: 'Unavailable' });
    http.expectOne(request => request.url === '/api/monthly-summary').flush(summary('2026-12-01'));
    await first;
    expect(store.entriesLoading()).toBe(false);
    expect(store.entriesLoadFailed()).toBe(true);
    const retry = store.loadEntries();
    expect(store.entriesLoading()).toBe(true);
    http.expectOne(request => request.url === '/api/entries').flush([]);
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2026-12-01'));
    await retry;
    expect(store.entriesLoadFailed()).toBe(false);
    expect(store.entries()).toEqual([]);
  });

  it('loads and creates an own category after authentication', async () => {
    await authenticate();
    store.categoryName = 'Mercado';
    store.categoryKind = 'DESPESA';

    const creation = store.createCategory();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/categories');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Mercado', kind: 'DESPESA' });
    request.flush({ id: 2, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true });
    await Promise.resolve();
    http.expectOne('/api/categories').flush([
      { id: 2, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true }
    ]);
    await creation;
    expect(store.categories()[0].name).toBe('Mercado');
  });

  it('deactivates a category for new records and can reactivate it', async () => {
    const category = { id: 4, ownerUserId: 1, name: 'Mercado',
      kind: 'DESPESA' as const, active: true };
    await authenticate({ categories: [category] });
    store.entryCategoryId = 4;
    store.purchaseCategoryId = 4;
    const deactivation = store.setCategoryActive(category, false);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const patch = http.expectOne('/api/categories/4/status');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ active: false });
    patch.flush({ ...category, active: false });
    await deactivation;
    expect(store.categories()[0].active).toBe(false);
    expect(store.expenseCategories()).toHaveLength(0);
    expect(store.entryCategoryId).toBeNull();
    expect(store.purchaseCategoryId).toBeNull();

    const reactivation = store.setCategoryActive(store.categories()[0], true);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/categories/4/status').flush(category);
    await reactivation;
    expect(store.expenseCategories()).toHaveLength(1);
    expect(store.notice()).toBe('Categoria reativada.');
  });

  it('registers an occurred expense and reloads the estimated balance', async () => {
    const wallet = { id: 3, ownerUserId: 1, name: 'Dinheiro',
      initialBalance: 100, estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    await authenticate({
      wallets: [wallet],
      categories: [{ id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true }],
    });
    store.entryWalletId = 3;
    store.entryCategoryId = 4;
    store.entryNecessity = 'NECESSARIA';
    store.entryDescription = 'Compra';
    store.entryAmount = 25.50;
    store.entryDate = '2026-01-01';

    const creation = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/entries');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      walletId: 3, categoryId: 4, description: 'Compra',
      amount: 25.5, occurredOn: '2026-01-01', entityId: null,
      necessity: 'NECESSARIA', newEntityName: null
    });
    request.flush({ id: 5 });
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([{ ...wallet, estimatedBalance: 74.5 }]);
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('month') === '2026-01').flush([{
        id: 5, ownerUserId: 1, walletId: 3, categoryId: 4, kind: 'DESPESA',
        description: 'Compra', amount: 25.5, occurredOn: '2026-01-01',
        referenceMonth: '2026-01-01', status: 'EFETIVADO'
      }]);
    http.expectOne(request => request.url === '/api/monthly-summary' &&
      request.params.get('month') === '2026-01').flush(summary('2026-01-01', 25.5));
    await creation;
    expect(store.entries()[0].description).toBe('Compra');
    expect(store.wallets()[0].estimatedBalance).toBe(74.5);
    expect(store.monthlySummary()?.realizedExpenses).toBe(25.5);
    expect(store.entryDescription).toBe('');
  });

  it('creates a counterparty and filters both the entries and the monthly summary', async () => {
    await authenticate();
    store.counterpartyName = 'Mercado';

    const creation = store.createCounterparty();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const post = http.expectOne('/api/entities');
    expect(post.request.body).toEqual({ name: 'Mercado' });
    post.flush({ id: 5, ownerUserId: 1, name: 'Mercado', active: true });
    await Promise.resolve();
    http.expectOne('/api/entities').flush([
      { id: 5, ownerUserId: 1, name: 'Mercado', active: true }
    ]);
    await creation;

    store.filterEntityId = 5;
    const loading = store.loadEntries();
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('entityId') === '5').flush([]);
    http.expectOne(request => request.url === '/api/monthly-summary' &&
      request.params.get('entityId') === '5').flush(summary('2026-11-01', 15));
    await loading;
    expect(store.counterparties()[0].name).toBe('Mercado');
    expect(store.monthlySummary()?.realizedExpenses).toBe(15);
  });

  it('deactivates and reactivates a counterparty without removing historical filters', async () => {
    const entity = { id: 5, ownerUserId: 1, name: 'Mercado', active: true };
    await authenticate({ entities: [entity] });
    store.entryEntityId = 5;
    store.filterEntityId = 5;

    const deactivation = store.setCounterpartyActive(5, false);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const patch = http.expectOne('/api/entities/5/status');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ active: false });
    patch.flush({ ...entity, active: false });
    await deactivation;
    expect(store.entryEntityId).toBeNull();
    expect(store.filterEntityId).toBe(5);
    expect(store.counterparties()[0].active).toBe(false);

    const reactivation = store.setCounterpartyActive(5, true);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/entities/5/status').flush(entity);
    await reactivation;

    store.entries.set([{
      id: 7, ownerUserId: 1, walletId: 3, categoryId: 4, entityId: 5,
      entityName: 'Mercado', kind: 'DESPESA', status: 'EFETIVADO',
      description: 'Compra antiga', amount: 10, occurredOn: '2026-01-01',
      expectedOn: null, referenceMonth: '2026-01-01', effectiveAt: '2026-01-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z', seriesId: null,
      occurrenceNumber: null, totalOccurrences: null, necessity: null
    }]);
    store.startRenameEntity(entity);
    store.editingEntityName = 'Mercadinho';
    const renaming = store.renameCounterparty();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const rename = http.expectOne('/api/entities/5');
    expect(rename.request.method).toBe('PUT');
    expect(rename.request.body).toEqual({ name: 'Mercadinho' });
    rename.flush({ ...entity, name: 'Mercadinho' });
    await renaming;
    expect(store.entries()[0].entityName).toBe('Mercadinho');
    expect(store.counterparties()[0].name).toBe('Mercadinho');
  });

  it('requires confirmation and removes an unlinked counterparty', async () => {
    await authenticate({
      entities: [{ id: 5, ownerUserId: 1, name: 'Sem vínculos', active: true }]
    });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    try {
      await store.deleteCounterparty(5);
      expect(store.counterparties()).toHaveLength(1);
      http.expectNone('/api/entities/5');
      confirm.mockReturnValue(true);
      const deletion = store.deleteCounterparty(5);
      http.expectOne('/api/auth/csrf').flush(null);
      await Promise.resolve();
      const request = http.expectOne('/api/entities/5');
      expect(request.request.method).toBe('DELETE');
      request.flush(null);
      await deletion;
      expect(store.counterparties()).toHaveLength(0);
    } finally {
      confirm.mockRestore();
    }
  });

  it('plans a future entry without changing the balance and can cancel it', async () => {
    const wallet = { id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 100,
      estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    await authenticate({
      wallets: [wallet],
      categories: [{ id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true }],
    });
    store.entryMode = 'planned';
    store.entryWalletId = 3;
    store.entryCategoryId = 4;
    store.entryNecessity = 'NEUTRA';
    store.entryDescription = 'Conta futura';
    store.entryAmount = 25;
    store.entryDate = '2099-01-01';

    const creation = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const post = http.expectOne('/api/entries/planned');
    expect(post.request.body).toEqual({
      walletId: 3, categoryId: 4, entityId: null,
      description: 'Conta futura', amount: 25, expectedOn: '2099-01-01',
      necessity: 'NEUTRA', newEntityName: null
    });
    post.flush({ id: 5, status: 'PREVISTO' });
    await Promise.resolve();
    const planned = { id: 5, ownerUserId: 1, walletId: 3, categoryId: 4,
      entityId: null, entityName: null, kind: 'DESPESA', status: 'PREVISTO',
      description: 'Conta futura', amount: 25, expectedOn: '2099-01-01',
      occurredOn: null, referenceMonth: '2099-01-01', effectiveAt: null };
    http.expectOne('/api/wallets').flush([wallet]);
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('month') === '2099-01').flush([planned]);
    http.expectOne(request => request.url === '/api/monthly-summary' &&
      request.params.get('month') === '2099-01')
      .flush(summary('2099-01-01', 0, 25));
    await creation;
    expect(store.entries()[0].status).toBe('PREVISTO');
    expect(store.monthlySummary()?.projectedResult).toBe(-25);
    expect(store.wallets()[0].estimatedBalance).toBe(100);

    const cancellation = store.cancelEntry(5);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/entries/5/cancel').flush(null);
    await Promise.resolve();
    http.expectOne(request => request.url === '/api/entries').flush([]);
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2099-01-01'));
    await cancellation;
    expect(store.entries()).toHaveLength(0);
  });

  it('requires classification for new expenses but not for income', async () => {
    await authenticate({ categories: [
      { id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true },
      { id: 5, ownerUserId: 1, name: 'Trabalho', kind: 'RECEITA', active: true }
    ] });
    store.entryWalletId = 3;
    store.entryCategoryId = 4;
    store.entryDescription = 'Despesa';
    store.entryAmount = 10;
    store.entryDate = '2026-01-01';
    await store.createEntry();
    expect(store.error()).toContain('Classifique');
    http.expectNone('/api/auth/csrf');

    store.entryCategoryId = 5;
    const creation = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/entries');
    expect(request.request.body.necessity).toBeNull();
    request.flush({ id: 9 });
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([]);
    http.expectOne(r => r.url === '/api/entries').flush([]);
    http.expectOne(r => r.url === '/api/monthly-summary').flush(summary('2026-01-01'));
    await creation;
    expect(store.error()).toBe('');
  });

  it('creates and consults a monthly finite series', async () => {
    const wallet = { id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 100,
      estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    await authenticate({
      wallets: [wallet],
      categories: [{ id: 4, ownerUserId: 1, name: 'Aluguel', kind: 'DESPESA', active: true }],
    });
    store.entryMode = 'series';
    store.entryWalletId = 3;
    store.entryCategoryId = 4;
    store.entryNecessity = 'NECESSARIA';
    store.entryDescription = 'Aluguel';
    store.entryAmount = 40;
    store.entryDate = '2099-01-31';
    store.seriesCount = 3;
    const creation = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const post = http.expectOne('/api/entries/series');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      walletId: 3, categoryId: 4, description: 'Aluguel',
      amount: 40, firstExpectedOn: '2099-01-31',
      totalOccurrences: 3, entityId: null, necessity: 'NECESSARIA', newEntityName: null
    });
    const series = [1, 2, 3].map((number, index) => ({
      id: number + 10, ownerUserId: 1, walletId: 3, categoryId: 4,
      kind: 'DESPESA', entityId: null, entityName: null,
       seriesId: 8, occurrenceNumber: number, totalOccurrences: 3,
       necessity: 'NECESSARIA' as const,
      description: 'Aluguel', amount: 40, status: 'PREVISTO',
      expectedOn: ['2099-01-31', '2099-02-28', '2099-03-31'][index],
      occurredOn: null, referenceMonth: `2099-0${number}-01`,
      createdAt: '2026-11-15T10:00:00Z', effectiveAt: null
    }));
    post.flush(series);
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([wallet]);
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('month') === '2099-01').flush([series[0]]);
    http.expectOne(request => request.url === '/api/monthly-summary' &&
      request.params.get('month') === '2099-01')
      .flush(summary('2099-01-01', 0, 40));
    await creation;
    expect(store.selectedSeriesId()).toBe(8);
    expect(store.selectedSeries()).toHaveLength(3);
    expect(store.wallets()[0].estimatedBalance).toBe(100);

    const consultation = store.showSeries(8);
    const get = http.expectOne('/api/entries/series/8');
    expect(get.request.method).toBe('GET');
    get.flush([series[0], { ...series[1], status: 'CANCELADO' }, series[2]]);
    await consultation;
    expect(store.selectedSeries()[1].status).toBe('CANCELADO');

    store.closeSeries();
    expect(store.selectedSeriesId()).toBeNull();
    expect(store.selectedSeries()).toEqual([]);
  });

  it('registers a transfer and refreshes both balances without changing the monthly summary', async () => {
    const source = { id: 3, ownerUserId: 1, name: 'Origem', initialBalance: 100,
      estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    const destination = { ...source, id: 4, name: 'Destino', initialBalance: 5,
      estimatedBalance: 5 };
    await authenticate({ wallets: [source, destination] });
    store.transferSourceId = 3;
    store.transferDestinationId = 4;
    store.transferAmount = 150;
    store.transferDescription = 'Reserva';
    store.transferDateTime = '2026-01-02T10:30';

    const creation = store.createTransfer();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const post = http.expectOne('/api/transfers');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      sourceWalletId: 3, destinationWalletId: 4, amount: 150,
      description: 'Reserva', transferredAt: '2026-01-02T10:30:00Z'
    });
    post.flush({ id: 5 });
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([
      { ...source, estimatedBalance: -50 }, { ...destination, estimatedBalance: 155 }
    ]);
    http.expectOne(request => request.url === '/api/transfers' &&
      request.params.get('month') === '2026-01').flush([{
        id: 5, ownerUserId: 1, sourceWalletId: 3, destinationWalletId: 4,
        amount: 150, description: 'Reserva', transferredAt: '2026-01-02T10:30:00Z',
        createdAt: '2026-11-15T10:00:00Z'
      }]);
    await creation;
    expect(store.transfers()[0].amount).toBe(150);
    expect(store.wallets().map(wallet => wallet.estimatedBalance)).toEqual([-50, 155]);
    expect(store.monthlySummary()?.realizedResult).toBe(0);
    expect(store.transferMonth).toBe('2026-01');
    expect(store.transferWalletName(3)).toBe('Origem');
  });

  it('edits a planned occurrence and refreshes the selected month', async () => {
    const wallet = { id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 100,
      estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    await authenticate({
      wallets: [wallet],
      categories: [{ id: 4, ownerUserId: 1, name: 'Aluguel', kind: 'DESPESA', active: true }],
    });
    store.selectedMonth = '2099-01';
    const planned: Entry = {
      id: 11, ownerUserId: 1, walletId: 3, categoryId: 4,
      entityId: null, entityName: null, kind: 'DESPESA', status: 'PREVISTO',
      description: 'Aluguel', amount: 40, expectedOn: '2099-01-31', occurredOn: null,
      referenceMonth: '2099-01-01', effectiveAt: null, createdAt: '2026-11-15T10:00:00Z',
       seriesId: 8, occurrenceNumber: 1, totalOccurrences: 3, necessity: 'NEUTRA'
    };
    store.entries.set([planned]);

    store.startEditEntry(planned);
    expect(store.editingEntryId()).toBe(11);
    expect(store.editDescription).toBe('Aluguel');
    expect(store.editExpectedOn).toBe('2099-01-31');
    store.editDescription = 'Aluguel reajustado';
    store.editAmount = 35;
    store.editExpectedOn = '2099-02-28';

    const saving = store.saveEntryEdit('single');
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const put = http.expectOne('/api/entries/11');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({
      walletId: 3, categoryId: 4, description: 'Aluguel reajustado',
       amount: 35, expectedOn: '2099-02-28', entityId: null, necessity: 'NEUTRA'
    });
    put.flush({ ...planned, description: 'Aluguel reajustado', amount: 35,
      expectedOn: '2099-02-28', referenceMonth: '2099-02-01' });
    await Promise.resolve();
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('month') === '2099-01')
      .flush([{ ...planned, description: 'Aluguel reajustado', amount: 35,
        expectedOn: '2099-02-28', referenceMonth: '2099-02-01' }]);
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2099-01-01', 0, 35));
    await saving;
    expect(store.editingEntryId()).toBeNull();
    expect(store.editDescription).toBe('');
    expect(store.notice()).toContain('atualizado');
    expect(store.entries()[0].amount).toBe(35);
  });

  it('edits the current and future occurrences of a series', async () => {
    await authenticate();
    store.categories.set([{ id: 4, ownerUserId: 1, name: 'Internet',
      kind: 'DESPESA', active: true }]);
    store.selectedMonth = '2099-01';
    const occurrence = (number: number, status: Entry['status']): Entry => ({
      id: 10 + number, ownerUserId: 1, walletId: 3, categoryId: 4,
      entityId: null, entityName: null, kind: 'DESPESA', status,
      description: 'Internet', amount: 25,
      expectedOn: `2099-0${number}-28`, occurredOn: null,
      referenceMonth: `2099-0${number}-01`, effectiveAt: null,
      createdAt: '2026-11-15T10:00:00Z', seriesId: 8,
       occurrenceNumber: number, totalOccurrences: 4, necessity: 'NEUTRA'
    });
    const occurrences = [occurrence(1, 'PREVISTO'), occurrence(2, 'PREVISTO'),
      occurrence(3, 'CANCELADO'), occurrence(4, 'PREVISTO')];
    store.selectedSeriesId.set(8);
    store.selectedSeries.set(occurrences);

    store.startEditEntry(occurrences[1]);
    expect(store.editingSeriesId).toBe(8);
    expect(store.editingOccurrenceNumber).toBe(2);
    store.editDescription = 'Internet reajustada';
    store.editAmount = 30;
    store.editExpectedOn = '2099-02-28';

    const saving = store.saveEntryEdit('future');
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const put = http.expectOne('/api/entries/series/8/occurrences/2');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({
      walletId: 3, categoryId: 4, description: 'Internet reajustada',
       amount: 30, expectedOn: '2099-02-28', entityId: null, necessity: 'NEUTRA'
    });
    const edited = [occurrences[1], occurrences[3]].map(entry => ({
      ...entry, description: 'Internet reajustada', amount: 30
    }));
    put.flush(edited);
    await Promise.resolve();
    http.expectOne(request => request.url === '/api/entries' &&
      request.params.get('month') === '2099-01').flush([]);
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2099-01-01'));
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/entries/series/8').flush(edited);
    await saving;
    expect(store.editingEntryId()).toBeNull();
    expect(store.notice()).toContain('futuras');
    expect(store.selectedSeries()[0].amount).toBe(30);
  });

  it('keeps the edit form open and explains a conflict', async () => {
    await authenticate();
    const planned: Entry = {
      id: 11, ownerUserId: 1, walletId: 3, categoryId: 4,
      entityId: null, entityName: null, kind: 'DESPESA', status: 'PREVISTO',
      description: 'Aluguel', amount: 40, expectedOn: '2099-01-31', occurredOn: null,
      referenceMonth: '2099-01-01', effectiveAt: null, createdAt: '2026-11-15T10:00:00Z',
       seriesId: null, occurrenceNumber: null, totalOccurrences: null, necessity: null
    };
    store.entries.set([planned]);
    store.startEditEntry(planned);

    const saving = store.saveEntryEdit('single');
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/entries/11').flush(null, { status: 409, statusText: 'Conflict' });
    await saving;
    expect(store.error()).toContain('Somente lançamentos previstos');
    expect(store.editingEntryId()).toBe(11);
  });

  it('registers a credit card and keeps it in the list', async () => {
    await authenticate();
    store.cardName = 'Cartão Gold';
    store.cardInstitution = 'Banco Teste';
    store.cardClosingDay = 28;
    store.cardDueDay = 5;
    store.cardLimit = 5000;

    const creation = store.createCard();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/cards');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Cartão Gold', institutionName: 'Banco Teste', closingDay: 28, dueDay: 5,
      creditLimit: 5000, defaultPaymentWalletId: null
    });
    request.flush({ id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
      closingDay: 28, dueDay: 5, creditLimit: 5000, defaultPaymentWalletId: null, active: true });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([
      { id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
        closingDay: 28, dueDay: 5, creditLimit: 5000, defaultPaymentWalletId: null, active: true }
    ]);
    await creation;
    expect(store.cards().length).toBe(1);
    expect(store.cardLabel(3)).toBe('Cartão Gold');
    expect(store.cardLabel(9)).toBe('Cartão 9');
    expect(store.notice()).toContain('Cartão cadastrado');
  });

  it('loads the card catalog without requesting statements on the cards page', async () => {
    const loading = store.loadCardCatalog();
    const request = http.expectOne('/api/cards');
    expect(request.request.method).toBe('GET');
    request.flush([
      { id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
        closingDay: 28, dueDay: 5, creditLimit: null, defaultPaymentWalletId: null, active: true }
    ]);
    await loading;
    expect(store.cards()[0].name).toBe('Cartão Gold');
    expect(store.cardsLoadFailed()).toBe(false);
    expect(store.cardsLoading()).toBe(false);
  });

  it('creates the entity together with the record when a new name is typed', async () => {
    const wallet = { id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 100,
      estimatedBalance: 100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null };
    await authenticate({
      wallets: [wallet],
      categories: [{ id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true }]
    });
    store.entryWalletId = 3;
    store.entryCategoryId = 4;
    store.entryDescription = 'Compra';
    store.entryAmount = 15;
    store.entryDate = '2026-01-01';
    store.entryNecessity = 'NEUTRA';
    store.entryNewEntityName = '  Padaria Nova  ';

    const creation = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/entries');
    expect(request.request.body.entityId).toBeNull();
    expect(request.request.body.newEntityName).toBe('Padaria Nova');
    request.flush({ id: 9, entityId: 12, entityName: 'Padaria Nova' });
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([wallet]);
    http.expectOne(r => r.url === '/api/entries').flush([]);
    http.expectOne(r => r.url === '/api/monthly-summary').flush(summary('2026-01-01'));
    await creation;
    expect(store.entryNewEntityName).toBe('');
    expect(store.error()).toBe('');
  });

  it('prefers the selected entity over a leftover new-entity name', () => {
    store.entryEntityId = 5;
    store.entryNewEntityName = 'Rascunho antigo';
    expect(store.entryEntityPayload()).toEqual({ entityId: 5, newEntityName: null });

    store.entryEntityId = null;
    expect(store.entryEntityPayload()).toEqual({ entityId: null, newEntityName: 'Rascunho antigo' });

    store.entryNewEntityName = '   ';
    expect(store.entryEntityPayload()).toEqual({ entityId: null, newEntityName: null });

    store.purchaseEntityId = 9;
    store.purchaseNewEntityName = 'Outro rascunho';
    expect(store.purchaseEntityPayload()).toEqual({ entityId: 9, newEntityName: null });
  });

  it('corrects the entity of an effected entry without changing its amounts', async () => {
    const entry: Entry = {
      id: 11, ownerUserId: 1, walletId: 3, categoryId: 4,
      entityId: 5, entityName: 'Fornecedor A', kind: 'DESPESA', status: 'EFETIVADO',
      description: 'Compra', amount: 40, expectedOn: null, occurredOn: '2026-01-05',
      referenceMonth: '2026-01-01', effectiveAt: '2026-01-05T00:00:00Z',
      createdAt: '2026-01-05T00:00:00Z', seriesId: null, occurrenceNumber: null,
      totalOccurrences: null, necessity: 'NECESSARIA'
    };
    await authenticate({ entries: [entry] });
    store.startEntryEntityEdit(entry);
    expect(store.editingEntryEntityId).toBe(11);
    expect(store.entryEntitySelection).toBe(5);
    store.entryEntitySelection = 6;

    const saving = store.saveEntryEntity();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/entries/11/entity');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ entityId: 6 });
    request.flush({ ...entry, entityId: 6, entityName: 'Fornecedor B' });
    await Promise.resolve();
    http.expectOne(r => r.url === '/api/entries').flush([]);
    http.expectOne(r => r.url === '/api/monthly-summary').flush(summary('2026-01-01'));
    await saving;
    expect(store.editingEntryEntityId).toBeNull();
    expect(store.notice()).toContain('Entidade');
  });

  it('corrects the entity of a card purchase and reloads the statements', async () => {
    await authenticate({ cards: [] });
    const installment = { id: 11, purchaseId: 7, statementId: 21, number: 1, amount: 50,
      status: 'ATIVA' as const, description: 'Compras', categoryId: 4, categoryName: 'Mercado',
      entityId: 5, entityName: 'Fornecedor A', statementDueDate: '2026-12-05',
      statementReferenceMonth: '2026-12' };
    store.startPurchaseEntityEdit(installment);
    expect(store.editingPurchaseEntityId).toBe(7);
    store.purchaseEntitySelection = null;

    const saving = store.savePurchaseEntity();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/card-purchases/7/entity');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ entityId: null });
    request.flush({ id: 7, status: 'ATIVA' });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([]);
    http.expectOne(r => r.url === '/api/card-statements').flush([]);
    http.expectOne(r => r.url === '/api/card-credits').flush([]);
    await saving;
    expect(store.editingPurchaseEntityId).toBeNull();
    expect(store.notice()).toContain('Entidade');
  });

  it('registers a card purchase and refreshes the statements of the month', async () => {
    await authenticate({
      cards: [{ id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
        closingDay: 28, dueDay: 5, creditLimit: null, defaultPaymentWalletId: null,
        active: true }],
      categories: [{ id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true }]
    });
    store.statementMonth = '2026-12';
    store.purchaseCardId = 3;
    store.purchaseCategoryId = 4;
    store.purchaseDescription = 'Compras do mês';
    store.purchaseAmount = 100;
    store.purchaseDate = '2026-11-10';
    store.purchaseInstallments = 2;
    store.purchaseNecessity = 'NECESSARIA';

    const registration = store.registerCardPurchase();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/card-purchases');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      cardId: 3, categoryId: 4, description: 'Compras do mês', amount: 100,
      purchasedOn: '2026-11-10', installments: 2, entityId: null, necessity: 'NECESSARIA',
      newEntityName: null
    });
    request.flush({
      id: 7, ownerUserId: 1, cardId: 3, categoryId: 4, entityId: null,
      description: 'Compras do mês', totalAmount: 100, purchasedOn: '2026-11-10',
      installmentCount: 2, necessity: 'NECESSARIA', status: 'ATIVA',
      installments: [
        { id: 11, purchaseId: 7, statementId: 21, number: 1, amount: 50, status: 'ATIVA',
          description: 'Compras do mês', categoryId: 4, categoryName: 'Mercado',
          entityId: null, entityName: null, statementDueDate: '2026-12-05',
          statementReferenceMonth: '2026-12' }
      ]
    });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([
      { id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
        closingDay: 28, dueDay: 5, creditLimit: null, defaultPaymentWalletId: null,
        active: true }
    ]);
    http.expectOne(request => request.url === '/api/card-statements' &&
      request.params.get('month') === '2026-12').flush([
      { id: 21, ownerUserId: 1, cardId: 3, referenceMonth: '2026-12',
        closingDate: '2026-11-28', dueDate: '2026-12-05', status: 'ABERTA', totalAmount: 50,
        installments: [
          { id: 11, purchaseId: 7, statementId: 21, number: 1, amount: 50, status: 'ATIVA',
            description: 'Compras do mês', categoryId: 4, categoryName: 'Mercado',
            entityId: null, entityName: null, statementDueDate: '2026-12-05',
            statementReferenceMonth: '2026-12' }
        ] }
    ]);
    http.expectOne(request => request.url === '/api/card-credits' &&
      request.params.get('month') === '2026-12').flush([]);
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2026-11-01', 0, 50));
    await registration;
    expect(store.cardStatements().length).toBe(1);
    expect(store.cardStatements()[0].totalAmount).toBe(50);
    expect(store.statementCardId).toBe(3);
    expect(store.monthlySummary()?.plannedExpenses).toBe(50);
    expect(store.notice()).toContain('Compra registrada');
  });

  it('keeps a saved card purchase confirmed when refreshing statements fails', async () => {
    await authenticate({
      cards: [{ id: 3, ownerUserId: 1, name: 'Cartão Gold',
        institutionName: 'Banco Teste', closingDay: 28, dueDay: 5,
        creditLimit: null, defaultPaymentWalletId: null, active: true }],
      categories: [{ id: 4, ownerUserId: 1, name: 'Mercado',
        kind: 'DESPESA', active: true }]
    });
    store.entryPaymentMethod = 'card';
    store.entryCategoryId = 4;
    store.purchaseCardId = 3;
    store.entryNecessity = 'NECESSARIA';
    store.entryAmount = 45.99;
    store.entryDescription = '';
    store.entryDate = '2026-11-10';

    const registration = store.createEntry();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const post = http.expectOne('/api/card-purchases');
    expect(post.request.body.description).toBe('');
    post.flush({
      id: 7, installments: [{ statementReferenceMonth: '2026-12' }]
    });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([]);
    http.expectOne(request => request.url === '/api/card-statements')
      .flush(null, { status: 503, statusText: 'Unavailable' });
    http.expectOne(request => request.url === '/api/card-credits').flush([]);
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2026-11-01'));
    await registration;

    expect(store.entryAmount).toBeNull();
    expect(store.notice()).toContain('Primeira parcela em 2026-12');
    expect(store.error()).toContain('compra foi salva');
    expect(store.cardsLoadFailed()).toBe(true);
  });

  it('pays a closed statement and refreshes the balance view', async () => {
    await authenticate({
      wallets: [{ id: 6, ownerUserId: 1, name: 'Conta corrente', initialBalance: 500,
        estimatedBalance: 500, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null }]
    });
    store.statementMonth = '2026-12';
    store.paymentStatementId = 21;
    store.paymentWalletId = 6;
    store.paymentDateTime = '2026-12-05T10:30';

    const payment = store.payStatement();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/card-statements/21/payments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      walletId: 6, paidOn: '2026-12-05T10:30:00Z'
    });
    request.flush({ id: 5, ownerUserId: 1, statementId: 21, walletId: 6, amount: 50,
      paidOn: '2026-12-05T10:30:00Z', createdAt: '2026-11-16T10:00:00Z' });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([]);
    http.expectOne(request => request.url === '/api/card-statements' &&
      request.params.get('month') === '2026-12').flush([]);
    http.expectOne(request => request.url === '/api/card-credits' &&
      request.params.get('month') === '2026-12').flush([]);
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2026-11-01', 50, 0));
    await payment;
    expect(store.paymentStatementId).toBeNull();
    expect(store.notice()).toContain('Fatura paga');
    expect(store.monthlySummary()?.realizedExpenses).toBe(50);
  });

  it('reverses a purchase and reloads credits and monthly totals', async () => {
    await authenticate();
    store.statementMonth = '2026-12';
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);
    try {
      const reversal = store.reverseCardPurchase(7);
      http.expectOne('/api/auth/csrf').flush(null);
      await Promise.resolve();
      const request = http.expectOne('/api/card-purchases/7/reversal');
      expect(request.request.method).toBe('POST');
      request.flush({ id: 7, status: 'ESTORNADA' });
      await Promise.resolve();
      http.expectOne('/api/cards').flush([]);
      http.expectOne(request => request.url === '/api/card-statements' &&
        request.params.get('month') === '2026-12').flush([]);
      http.expectOne(request => request.url === '/api/card-credits' &&
        request.params.get('month') === '2026-12').flush([
        { id: 1, purchaseId: 7, cardId: 3, originalAmount: 50,
          availableAmount: 50, createdAt: '2026-12-05T10:00:00Z' }
      ]);
      await new Promise(resolve => setTimeout(resolve, 0));
      http.expectOne(request => request.url === '/api/monthly-summary')
        .flush(summary('2026-11-01', 50, 0));
      await reversal;
      expect(store.cardCredits()[0].availableAmount).toBe(50);
      expect(store.notice()).toContain('Compra estornada');
    } finally {
      confirmation.mockRestore();
    }
  });

  it('edits a card and deactivates it without discarding its history', async () => {
    const card = { id: 3, ownerUserId: 1, name: 'Cartão Gold',
      institutionName: 'Banco Teste', closingDay: 28, dueDay: 5,
      creditLimit: null, defaultPaymentWalletId: null, active: true };
    await authenticate({ cards: [card] });
    store.beginCardEdit(card);
    store.editingCardName = 'Cartão Platinum';
    const editing = store.updateCard();
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const request = http.expectOne('/api/cards/3');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.name).toBe('Cartão Platinum');
    request.flush({ ...card, name: 'Cartão Platinum' });
    await Promise.resolve();
    http.expectOne('/api/cards').flush([{ ...card, name: 'Cartão Platinum' }]);
    await editing;
    expect(store.editingCardId).toBeNull();

    const deactivation = store.setCardActive(store.cards()[0], false);
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    const statusRequest = http.expectOne('/api/cards/3/status');
    expect(statusRequest.request.method).toBe('PATCH');
    expect(statusRequest.request.body).toEqual({ active: false });
    statusRequest.flush({ ...card, name: 'Cartão Platinum', active: false });
    await deactivation;
    expect(store.cards()[0].active).toBe(false);
    expect(store.cards()[0].name).toBe('Cartão Platinum');
  });
});
