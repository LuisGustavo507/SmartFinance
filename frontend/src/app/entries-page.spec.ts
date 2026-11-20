import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EntriesPage } from './pages/entries-page';
import { FinanceStore } from './finance-store';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';

const entry = {
  id: 11, ownerUserId: 1, walletId: 3, categoryId: 4,
  entityId: null, entityName: null, kind: 'DESPESA' as const,
  status: 'PREVISTO' as const, description: 'Aluguel', amount: 40,
  expectedOn: '2099-01-31', occurredOn: null,
  referenceMonth: '2099-01-01', effectiveAt: null,
  createdAt: '2026-11-15T10:00:00Z',
  seriesId: 8, occurrenceNumber: 1, totalOccurrences: 3, necessity: null
};

describe('EntriesPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EntriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows only card-specific fields for a credit-card purchase', async () => {
    const fixture = TestBed.createComponent(EntriesPage);
    const store = TestBed.inject(FinanceStore);
    store.entryPaymentMethod = 'card';
    store.cards.set([{ id: 3, ownerUserId: 1, name: 'Cartão Gold',
      institutionName: 'Banco Teste', closingDay: 28, dueDay: 5,
      creditLimit: null, defaultPaymentWalletId: null, active: true }]);
    store.categories.set([
      { id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true },
      { id: 5, ownerUserId: 1, name: 'Salário', kind: 'RECEITA', active: true }
    ]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('#entry-wallet')).toBeNull();
    expect(page.querySelector('#entry-mode')).toBeNull();
    expect(page.querySelector('#purchase-card option')?.textContent).toContain('Selecione');
    expect(page.textContent).toContain('Cartão Gold (Banco Teste)');
    expect(page.textContent).toContain('Valor total da compra');
    expect(page.textContent).toContain('Parcelas (1 a 120)');
    expect(page.querySelector('#entry-description')?.hasAttribute('required')).toBe(false);
    expect(page.textContent).not.toContain('Salário (Receita)');
  });

  it('labels installment amounts and previews the total without changing the typed value', async () => {
    const fixture = TestBed.createComponent(EntriesPage);
    const store = TestBed.inject(FinanceStore);
    store.entryPaymentMethod = 'card';
    store.purchaseInstallments = 3;
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const mode = page.querySelector('#purchase-amount-mode') as HTMLSelectElement;
    expect(mode.value).toBe('total');
    mode.value = 'installment';
    mode.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(page.textContent).toContain('Data da primeira parcela a registrar');
    expect(page.textContent).toContain('Parcelas a registrar');
    expect(page.textContent).toContain('Parcelas anteriores não serão lançadas');

    const amount = page.querySelector('#entry-amount') as HTMLInputElement;
    amount.value = '5000';
    amount.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(store.entryAmount).toBe(50);
    expect(page.textContent).toMatch(/3 parcelas de R\$\s50,00/);
    expect(page.textContent).toMatch(/total a registrar: R\$\s150,00/);
    expect(page.textContent).toContain('1/3');

    mode.value = 'total';
    mode.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(store.entryAmount).toBe(50);
    expect(page.textContent).toMatch(/R\$\s50,00 serão divididos em 3 parcelas/);
    expect(page.textContent).toContain('Data da compra');
  });

  it('renders the month summary, the series marker and the series details', async () => {
    const fixture = TestBed.createComponent(EntriesPage);
    const store = TestBed.inject(FinanceStore);
    store.selectedMonth = '2099-01';
    store.wallets.set([{ id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 0,
      estimatedBalance: 0, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null }]);
    store.categories.set([{ id: 4, ownerUserId: 1, name: 'Aluguel', kind: 'DESPESA', active: true }]);
    store.entryCategoryId = 4;
    store.entries.set([entry]);
    store.monthlySummary.set({
      month: '2099-01-01', realizedIncome: 0, realizedExpenses: 0, realizedResult: 0,
      plannedIncome: 0, plannedExpenses: 40, projectedResult: -40,
      necessityTotals: [
        { necessity: 'NECESSARIA', realizedExpenses: 0, plannedExpenses: 40 },
        { necessity: null, realizedExpenses: 5, plannedExpenses: 0 }
      ],
      categoryTotals: [
        { categoryId: 4, categoryName: 'Aluguel', kind: 'DESPESA',
          realizedIncome: 0, realizedExpenses: 10, plannedIncome: 0, plannedExpenses: 40 },
        { categoryId: 9, categoryName: 'Salário', kind: 'RECEITA',
          realizedIncome: 90, realizedExpenses: 0, plannedIncome: 0, plannedExpenses: 0 }
      ]
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Lançamentos');
    expect(compiled.querySelectorAll('#entry-mode option')).toHaveLength(3);
    expect(compiled.textContent).toContain('série 1/3');
    expect(compiled.textContent).toContain('Não classificada');
    expect(compiled.textContent).toContain('Despesas por necessidade');
    expect(compiled.textContent).toContain('Por categoria');
    expect(compiled.textContent).toContain('Aluguel');
    expect(compiled.textContent).toContain('Salário');
    expect(compiled.textContent).toMatch(/previsto\s−\sR\$\s40,00/);
    expect(compiled.textContent).toMatch(/\+\sR\$\s90,00/);
    expect(compiled.textContent).toContain('Necessária · previstas');
    expect(compiled.textContent).toContain('Não classificada · realizadas');
    expect(compiled.querySelector('#entry-necessity')).not.toBeNull();
    expect(compiled.textContent).toContain('Resumo de janeiro de 2099');
    expect(compiled.textContent).toMatch(/R\$\s40,00/);

    const showSeries = vi.spyOn(store, 'showSeries').mockResolvedValue(undefined);
    const button = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Ver série')) as HTMLButtonElement;
    button.click();
    expect(showSeries).toHaveBeenCalledWith(8);

    store.selectedSeriesId.set(8);
    store.selectedSeries.set([
      entry,
      { ...entry, id: 12, status: 'CANCELADO', occurrenceNumber: 2,
        expectedOn: '2099-02-28', referenceMonth: '2099-02-01' }
    ]);
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Série 8');
    expect(compiled.textContent).toContain('2 ocorrências geradas');
    expect(compiled.textContent).toContain('CANCELADO');
    const cancel = [...compiled.querySelectorAll('button')]
      .filter(candidate => candidate.textContent?.includes('Cancelar'));
    expect(cancel.length).toBeGreaterThan(0);

    const editButtons = [...compiled.querySelectorAll('button')]
      .filter(candidate => candidate.textContent?.trim() === 'Editar');
    expect(editButtons.length).toBeGreaterThan(0);
    (editButtons[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    fixture.detectChanges();
    expect(store.editDescription).toBe('Aluguel');
    expect(compiled.querySelector('#entry-edit')).not.toBeNull();
    expect((compiled.querySelector('#edit-description') as HTMLInputElement).value)
      .toBe('Aluguel');
    expect(store.editExpectedOn).toBe('2099-01-31');
    expect((compiled.querySelector('#edit-necessity') as HTMLSelectElement).value)
      .toContain('null');

    const saveFuture = vi.spyOn(store, 'saveEntryEdit').mockResolvedValue(undefined);
    const futureButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Salvar esta e as futuras')) as HTMLButtonElement;
    expect(futureButton).toBeTruthy();
    futureButton.click();
    expect(saveFuture).toHaveBeenCalledWith('future');

    const closeEdit = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Cancelar edição')) as HTMLButtonElement;
    closeEdit.click();
    fixture.detectChanges();
    expect(compiled.querySelector('#entry-edit')).toBeNull();

    store.selectedSeriesId.set(null);
    store.selectedSeries.set([]);
    fixture.detectChanges();
    expect(compiled.textContent).not.toContain('Série 8');
  });

  it('does not offer necessity for income categories', () => {
    const fixture = TestBed.createComponent(EntriesPage);
    const store = TestBed.inject(FinanceStore);
    store.categories.set([{ id: 5, ownerUserId: 1, name: 'Salário',
      kind: 'RECEITA', active: true }]);
    store.entryCategoryId = 5;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#entry-necessity')).toBeNull();
    expect(fixture.nativeElement.querySelector('#purchase-amount-mode')).toBeNull();
  });

  it('hides the new-entity field when an existing entity is selected', () => {
    const fixture = TestBed.createComponent(EntriesPage);
    const store = TestBed.inject(FinanceStore);
    store.counterparties.set([{ id: 5, ownerUserId: 1, name: 'Empregador', active: true }]);
    store.entryEntityId = 5;
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('#entry-entity')).not.toBeNull();
    expect(page.querySelector('#entry-new-entity')).toBeNull();
    expect(page.textContent).not.toContain('Ou nova entidade');
  });

  it('offers the new-entity field while the selection is "Sem entidade"', () => {
    const fixture = TestBed.createComponent(EntriesPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('#entry-new-entity')).not.toBeNull();
    expect(page.textContent).toContain('Ou nova entidade');
  });
});
