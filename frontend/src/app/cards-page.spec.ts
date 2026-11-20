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

describe('CardsPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows only the card registration and management, not statements', async () => {
    const store = TestBed.inject(FinanceStore);
    const loadCatalog = vi.spyOn(store, 'loadCardCatalog').mockResolvedValue(undefined);
    store.cards.set([card]);
    store.cardStatements.set([{
      id: 21, ownerUserId: 1, cardId: 3, referenceMonth: '2026-12',
      closingDate: '2026-11-28', dueDate: '2026-12-05', status: 'ABERTA', totalAmount: 50,
      appliedCredits: 0, projectedCredits: 0, closedAmount: null,
      paidAmount: 0, paidOn: null, installments: []
    }]);
    store.cardName = 'Cartão Novo';
    store.cardInstitution = 'Banco Teste';

    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(loadCatalog).toHaveBeenCalled();
    expect(compiled.querySelector('h1')?.textContent).toContain('Cartões');
    expect(compiled.textContent).toContain('Cartão Gold');
    expect(compiled.textContent).not.toContain('Fatura Cartão Gold');
    expect(compiled.querySelector('#statement-month')).toBeNull();
    expect(compiled.textContent).not.toContain('Pagar fatura');

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

  it('shows an empty card catalog and a retry when loading fails', () => {
    const store = TestBed.inject(FinanceStore);
    const loadCatalog = vi.spyOn(store, 'loadCardCatalog').mockResolvedValue(undefined);
    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Nenhum cartão cadastrado');
    store.cardsLoadFailed.set(true);
    fixture.detectChanges();
    expect(compiled.textContent).toContain('Não foi possível carregar os cartões.');
    const retry = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Tentar novamente')) as HTMLButtonElement;
    retry.click();
    expect(loadCatalog).toHaveBeenCalledTimes(2);
  });
});
