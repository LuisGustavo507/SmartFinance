import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CardStatement } from './api';
import { FinanceStore } from './finance-store';
import { StatementsPage } from './pages/statements-page';

const cards = [
  { id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
    closingDay: 28, dueDay: 5, creditLimit: null, defaultPaymentWalletId: null, active: true },
  { id: 4, ownerUserId: 1, name: 'Cartão Prata', institutionName: 'Banco Teste',
    closingDay: 28, dueDay: 5, creditLimit: null, defaultPaymentWalletId: null, active: false }
];

const installment = {
  id: 11, purchaseId: 7, statementId: 21, number: 1, amount: 50, status: 'ATIVA' as const,
  description: 'Compra Gold', categoryId: 5, categoryName: 'Mercado',
  entityId: null, entityName: null, statementDueDate: '2026-12-05',
  statementReferenceMonth: '2026-12'
};

const statement: CardStatement = {
  id: 21, ownerUserId: 1, cardId: 3, referenceMonth: '2026-12',
  closingDate: '2026-11-28', dueDate: '2026-12-05', status: 'ABERTA',
  totalAmount: 50, appliedCredits: 0, projectedCredits: 5,
  closedAmount: null, paidAmount: 0, paidOn: null, installments: [installment]
};

describe('StatementsPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatementsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
  });

  it('shows only the selected card and month, with the backend statement total', () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set(cards);
    store.statementCardId = 3;
    store.statementMonth = '2026-12';
    store.cardStatements.set([
      { ...statement, id: 23, referenceMonth: '2026-11', totalAmount: 30,
        installments: [{ ...installment, id: 13, description: 'Compra Novembro', amount: 30 }] },
      statement,
      { ...statement, id: 22, cardId: 4, totalAmount: 70,
        installments: [{ ...installment, id: 12, description: 'Compra Prata', amount: 70 }] }
    ]);
    store.cardCredits.set([
      { id: 1, purchaseId: 7, cardId: 3, originalAmount: 8, availableAmount: 8, createdAt: '2026-12-01T00:00:00Z' },
      { id: 2, purchaseId: 8, cardId: 4, originalAmount: 20, availableAmount: 20, createdAt: '2026-12-01T00:00:00Z' }
    ]);

    const fixture = TestBed.createComponent(StatementsPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('h1')?.textContent).toContain('Faturas');
    expect(page.textContent).toContain('Fatura de Cartão Gold');
    expect(page.textContent).toContain('Compra Gold');
    expect(page.textContent).toContain('Gastos previstos');
    expect(page.textContent).toContain('Total de gastos da fatura');
    const overview = page.querySelector('.statement-overview') as HTMLElement;
    const activeList = page.querySelector('.statement-installments') as HTMLElement;
    expect(overview).not.toBeNull();
    expect(activeList).not.toBeNull();
    expect(overview.compareDocumentPosition(activeList) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(overview.textContent).toMatch(/R\$\s50,00/);
    expect(activeList.tabIndex).toBe(0);
    expect(page.textContent).toMatch(/R\$\s50,00/);
    expect(page.textContent).toContain('líquido previsto');
    expect(page.textContent).not.toContain('Compra Prata');
    expect(page.textContent).not.toContain('Compra Novembro');
    expect(page.textContent).toContain('Compra #7');
    expect(page.textContent).not.toContain('Compra #8');
    expect(page.querySelector('option')?.textContent).toContain('Selecione um cartão');
    expect(page.textContent).toContain('Cartão Prata (inativo)');
  });

  it('separates canceled and reversed items without hiding their history', () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set(cards);
    store.statementCardId = 3;
    store.statementMonth = '2026-12';
    store.cardStatements.set([{
      ...statement, status: 'FECHADA', totalAmount: 60, closedAmount: 60,
      installments: [
        installment,
        { ...installment, id: 12, status: 'CANCELADA', description: 'Cancelada', amount: 15 },
        { ...installment, id: 13, status: 'ESTORNADA', description: 'Estornada', amount: 10 }
      ]
    }]);

    const fixture = TestBed.createComponent(StatementsPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const active = page.querySelector('section[aria-label="Gastos da fatura"]')!;
    const history = page.querySelector('section[aria-label="Itens cancelados e estornados"]')!;
    expect(active.textContent).toContain('Compra Gold');
    expect(active.textContent).not.toContain('Cancelada');
    expect(history.textContent).toContain('Cancelada');
    expect(history.textContent).toContain('Estornada');
    expect(page.textContent).toMatch(/R\$\s60,00/);
    expect(page.textContent).toContain('Valor a pagar');
  });

  it('keeps payment available for a closed statement', () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set(cards);
    store.statementCardId = 3;
    store.statementMonth = '2026-12';
    store.paymentWalletId = 6;
    store.cardStatements.set([{ ...statement, status: 'FECHADA', closedAmount: 50 }]);
    store.wallets.set([{ id: 6, ownerUserId: 1, name: 'Conta corrente', initialBalance: 500,
      estimatedBalance: 500, initialBalanceDate: '2026-01-01', active: true,
      type: 'CARTEIRA_DINHEIRO', institutionName: null }]);

    const fixture = TestBed.createComponent(StatementsPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const pay = [...page.querySelectorAll('button')]
      .find(button => button.textContent?.includes('Pagar fatura')) as HTMLButtonElement;
    pay.click();
    fixture.detectChanges();
    expect(page.querySelector('#payment-wallet')).not.toBeNull();
    expect(page.querySelector('#payment-date')).not.toBeNull();
    const payStatement = vi.spyOn(store, 'payStatement').mockResolvedValue(undefined);
    const confirm = [...page.querySelectorAll('button')]
      .find(button => button.textContent?.includes('Confirmar pagamento')) as HTMLButtonElement;
    confirm.click();
    expect(payStatement).toHaveBeenCalled();
  });

  it('guides selection, missing cards, loading and retry after failure', () => {
    const store = TestBed.inject(FinanceStore);
    const reload = vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set(cards);
    const fixture = TestBed.createComponent(StatementsPage);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Selecione um cartão');

    store.cardsLoading.set(true);
    fixture.detectChanges();
    expect(page.textContent).toContain('Carregando faturas');
    store.cardsLoading.set(false);
    store.cardsLoadFailed.set(true);
    fixture.detectChanges();
    expect(page.textContent).toContain('Não foi possível carregar as faturas.');
    const retry = [...page.querySelectorAll('button')]
      .find(button => button.textContent?.includes('Tentar novamente')) as HTMLButtonElement;
    retry.click();
    expect(reload).toHaveBeenCalledTimes(2);

    store.cardsLoadFailed.set(false);
    store.cards.set([]);
    fixture.detectChanges();
    expect(page.textContent).toContain('Cadastrar cartão');
  });

  it('clears hidden edit and payment forms when switching card or month', () => {
    const store = TestBed.inject(FinanceStore);
    const reload = vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set(cards);
    const fixture = TestBed.createComponent(StatementsPage);
    fixture.detectChanges();

    store.paymentStatementId = 21;
    store.editingPurchaseEntityId = 7;
    fixture.componentInstance.cardChanged();
    expect(store.paymentStatementId).toBeNull();
    expect(store.editingPurchaseEntityId).toBeNull();

    store.paymentStatementId = 21;
    fixture.componentInstance.loadStatements();
    expect(store.paymentStatementId).toBeNull();
    expect(reload).toHaveBeenCalledTimes(2);
  });
});
