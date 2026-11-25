import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CardsPage } from './pages/cards-page';
import { FinanceStore } from './finance-store';
import { ActionConfirmation } from './action-confirmation';
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
    store.institutions.set([
      { id: 1, code: 'BANCO_TESTE', name: 'Banco Teste', active: true },
      { id: 2, code: 'OUTRA', name: 'Outra instituição', active: true }
    ]);
    store.cards.set([card]);
    store.cardStatements.set([{
      id: 21, ownerUserId: 1, cardId: 3, referenceMonth: '2026-12',
      closingDate: '2026-11-28', dueDate: '2026-12-05', status: 'ABERTA', totalAmount: 50,
      appliedCredits: 0, projectedCredits: 0, closedAmount: null,
      paidAmount: 0, paidOn: null, installments: []
    }]);
    store.cardName = 'Cartão Novo';
    store.cardInstitutionChoice = 'Banco Teste';

    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(loadCatalog).toHaveBeenCalled();
    expect(compiled.querySelector('h1')?.textContent).toContain('Cartões');
    const institutionSelect = compiled.querySelector('#card-institution') as HTMLSelectElement;
    expect(institutionSelect.tagName).toBe('SELECT');
    expect([...institutionSelect.options].map(option => option.value)).toContain('Banco Teste');
    expect(compiled.querySelector('#card-institution-name')).toBeNull();
    expect(compiled.textContent).toContain('Cartão Gold');
    expect(compiled.textContent).not.toContain('Fatura Cartão Gold');
    expect(compiled.querySelector('#statement-month')).toBeNull();
    expect(compiled.textContent).not.toContain('Pagar fatura');

    const createCard = vi.spyOn(store, 'createCard').mockResolvedValue(undefined);
    const cardButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.textContent?.includes('Cadastrar cartão')) as HTMLButtonElement;
    cardButton.click();
    expect(createCard).toHaveBeenCalled();
    const editButton = [...compiled.querySelectorAll('button')]
      .find(candidate => candidate.getAttribute('aria-label') === 'Editar cartão Cartão Gold') as HTMLButtonElement;
    editButton.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect((document.querySelector('#edit-card-name') as HTMLInputElement).value).toBe('Cartão Gold');
    expect((document.querySelector('#edit-card-institution') as HTMLSelectElement).value)
      .toBe('Banco Teste');
    expect(document.querySelector('#edit-card-institution-name')).toBeNull();
    expect(document.querySelector('.p-dialog')?.textContent).toContain('Salvar cartão');
  });

  it('asks for the institution name only when the card uses OUTRA', () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCardCatalog').mockResolvedValue(undefined);
    store.institutions.set([
      { id: 1, code: 'BANCO_TESTE', name: 'Banco Teste', active: true },
      { id: 2, code: 'OUTRA', name: 'Outra instituição', active: true }
    ]);
    store.cardInstitutionChoice = 'OUTRA';
    store.cardInstitutionName = 'Banco Novo';
    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#card-institution-name')).not.toBeNull();
    expect(store.cardInstitution).toBe('Banco Novo');
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

  it('requires confirmation before discarding edits in the card dialog', async () => {
    const store = TestBed.inject(FinanceStore);
    vi.spyOn(store, 'loadCardCatalog').mockResolvedValue(undefined);
    store.cards.set([card]);
    const ask = vi.spyOn(TestBed.inject(ActionConfirmation), 'ask').mockResolvedValue(false);
    const fixture = TestBed.createComponent(CardsPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    const trigger = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(button => button.getAttribute('aria-label') === 'Editar cartão Cartão Gold') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const name = document.querySelector('#edit-card-name') as HTMLInputElement;
    name.value = 'Outro nome';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(page.cardDirty()).toBe(true);
    const dialog = document.querySelector('.p-dialog') as HTMLElement;
    expect(dialog.textContent).toContain('Desativar');
    const cancel = [...dialog.querySelectorAll('button')]
      .find(button => button.textContent?.trim() === 'Cancelar') as HTMLButtonElement;
    cancel.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(store.editingCardId).toBe(3);
    expect(ask).toHaveBeenCalledWith('Descartar alterações',
      'Descartar as alterações não salvas?', 'Descartar');
  });
});
