import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CardsPage } from './pages/cards-page';
import { FinanceStore } from './finance-store';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';

const card = {
  id: 3, ownerUserId: 1, name: 'Cartão Gold', institutionName: 'Banco Teste',
  closingDay: 28, dueDay: 5, creditLimit: 5000, defaultPaymentWalletId: null, active: true
};

const installment = {
  id: 11, purchaseId: 7, statementId: 21, number: 1, amount: 50, status: 'ATIVA' as const,
  description: 'Compras do mês', categoryId: 4, categoryName: 'Mercado',
  entityId: null, entityName: null, statementDueDate: '2026-12-05',
  statementReferenceMonth: '2026-12'
};

describe('CardsPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows the cards, statements and link to record a purchase', async () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set([card]);
    store.categories.set([
      { id: 4, ownerUserId: 1, name: 'Mercado', kind: 'DESPESA', active: true },
      { id: 5, ownerUserId: 1, name: 'Salário', kind: 'RECEITA', active: true }
    ]);
    store.cardStatements.set([{
      id: 21, ownerUserId: 1, cardId: 3, referenceMonth: '2026-12',
      closingDate: '2026-11-28', dueDate: '2026-12-05', status: 'ABERTA', totalAmount: 50,
      appliedCredits: 0, projectedCredits: 0, closedAmount: null,
      paidAmount: 0, paidOn: null, installments: [installment]
    }]);
    store.cardName = 'Cartão Novo';
    store.cardInstitution = 'Banco Teste';
    store.purchaseCardId = 3;
    store.purchaseCategoryId = 4;
    store.purchaseDescription = 'Compras do mês';
    store.purchaseAmount = 50;

    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('Cartões');
    expect(compiled.textContent).toContain('Cartão Gold');
    expect(compiled.textContent).toContain('Fatura Cartão Gold');
    expect(compiled.textContent).toContain('Compras do mês');
    expect(compiled.textContent).toContain('05/12/2026');
    expect(compiled.textContent).toMatch(/R\$\s50,00/);
    expect(compiled.textContent).toContain('Aberta');
    expect(compiled.textContent).toContain('Estornar compra');

    expect(compiled.querySelector('#purchase-card')).toBeNull();
    expect(compiled.textContent).toContain('Registrar compra');
    expect(compiled.textContent).not.toContain('Salário');

    const createCard = vi.spyOn(store, 'createCard').mockResolvedValue(undefined);
    const cardButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Cadastrar cartão')) as HTMLButtonElement;
    cardButton.click();
    expect(createCard).toHaveBeenCalled();


    store.beginCardEdit(card);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect((compiled.querySelector('#edit-card-name') as HTMLInputElement).value).toBe('Cartão Gold');
    expect(compiled.textContent).toContain('Salvar cartão');
  });

  it('offers the payment of a closed statement', () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCards').mockResolvedValue(undefined);
    store.cards.set([card]);
    store.wallets.set([{ id: 6, ownerUserId: 1, name: 'Conta corrente', initialBalance: 500,
      estimatedBalance: 500, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null }]);
    store.cardStatements.set([{
      id: 22, ownerUserId: 1, cardId: 3, referenceMonth: '2026-11',
      closingDate: '2026-10-28', dueDate: '2026-11-05', status: 'FECHADA', totalAmount: 50,
      appliedCredits: 0, projectedCredits: 0, closedAmount: 50,
      paidAmount: 0, paidOn: null, installments: [installment]
    }]);
    store.paymentWalletId = 6;
    store.paymentDateTime = '2026-12-05T10:30';

    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Fechada');
    expect(compiled.textContent).toContain('Valor a pagar');

    const payButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Pagar fatura')) as HTMLButtonElement;
    expect(payButton).toBeTruthy();
    payButton.click();
    fixture.detectChanges();

    expect(compiled.querySelector('#payment-wallet')).not.toBeNull();
    expect(compiled.querySelector('#payment-date')).not.toBeNull();

    const payStatement = vi.spyOn(store, 'payStatement').mockResolvedValue(undefined);
    const confirmButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Confirmar pagamento')) as HTMLButtonElement;
    confirmButton.click();
    expect(payStatement).toHaveBeenCalled();
  });
});
