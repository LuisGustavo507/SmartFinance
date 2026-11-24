import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Api, Payable } from './api';
import { FinanceStore } from './finance-store';
import { PayablesPage } from './pages/payables-page';
import { ActionConfirmation } from './action-confirmation';

const payable: Payable = {
  id: 3, ownerUserId: 1, categoryId: 2, categoryName: 'Energia',
  entityId: null, entityName: null, description: 'Conta de luz',
  necessity: 'NECESSARIA', amount: 100, dueOn: '2026-11-10',
  status: 'ATRASADA', createdAt: '2026-11-01T10:00:00Z'
};

describe('PayablesPage', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      payables: vi.fn(() => of([payable])),
      payable: vi.fn(() => of(payable)),
      payablePayments: vi.fn(() => of([])),
      csrf: vi.fn(() => of(undefined)),
      createPayable: vi.fn(() => of(payable)),
      updatePayable: vi.fn(() => of(payable)),
      payPayable: vi.fn(() => of({})),
      reversePayablePayment: vi.fn(() => of({})),
      cancelPayable: vi.fn(() => of(undefined)),
      wallets: vi.fn(() => of([])),
      monthlySummary: vi.fn(() => of({
        month: '2026-11-01', realizedIncome: 0, realizedExpenses: 0, realizedResult: 0,
        plannedIncome: 0, plannedExpenses: 0, pendingExpenses: 100, projectedResult: -100
      }))
    };
    await TestBed.configureTestingModule({
      imports: [PayablesPage],
      providers: [{ provide: Api, useValue: api }, provideRouter([])]
    }).compileComponents();
  });

  it('explains the balance rule and lists overdue obligations even for another month', async () => {
    const fixture = TestBed.createComponent(PayablesPage);
    fixture.componentInstance.month = '2026-12';
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(api['payables']).toHaveBeenCalledWith('2026-12');
    expect(text).toContain('O dinheiro sai apenas ao confirmar o pagamento integral');
    expect(text).toContain('Conta de luz');
    expect(text).toContain('Atrasada');
    expect(text).toContain('Não registre a mesma despesa também em Lançamentos');
    const page = fixture.nativeElement as HTMLElement;
    const filter = page.querySelector('.filter') as HTMLElement;
    const list = page.querySelector('.payable-list') as HTMLElement;
    expect(list.tabIndex).toBe(0);
    expect(filter.compareDocumentPosition(list) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('automatically loads a new month and ignores an older response', async () => {
    const first = new Subject<Payable[]>();
    const second = new Subject<Payable[]>();
    api['payables'].mockReturnValueOnce(first).mockReturnValueOnce(second);
    const fixture = TestBed.createComponent(PayablesPage);
    const page = fixture.componentInstance;
    page.month = '2026-12';
    fixture.detectChanges();

    page.changeMonth('2027-01');
    expect(api['payables']).toHaveBeenNthCalledWith(1, '2026-12');
    expect(api['payables']).toHaveBeenNthCalledWith(2, '2027-01');
    second.next([{ ...payable, id: 4, description: 'Janeiro' }]);
    second.complete();
    await fixture.whenStable();
    first.next([payable]);
    first.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.payables()[0].description).toBe('Janeiro');
    expect(page.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Janeiro');
  });

  it('preserves input and reports an error if creating a payable fails', async () => {
    api['createPayable'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    const fixture = TestBed.createComponent(PayablesPage);
    const page = fixture.componentInstance;
    const store = TestBed.inject(FinanceStore);
    store.categories.set([{ id: 2, ownerUserId: 1, name: 'Energia',
      kind: 'DESPESA', active: true }]);
    fixture.detectChanges();
    await fixture.whenStable();
    page.categoryId = 2;
    page.necessity = 'NECESSARIA';
    page.description = 'Luz de novembro';
    page.amount = 100;
    page.dueOn = '2026-11-10';
    fixture.detectChanges();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(element => element.textContent?.includes('Cadastrar conta'))!;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(page.error()).toContain('Confira os dados');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
    expect(page.description).toBe('Luz de novembro');
    expect(page.amount).toBe(100);
  });

  it('registers one integral payment and refreshes visible state', async () => {
    const fixture = TestBed.createComponent(PayablesPage);
    const page = fixture.componentInstance;
    const store = TestBed.inject(FinanceStore);
    store.wallets.set([{ id: 5, ownerUserId: 1, name: 'Conta', type: 'CONTA_CORRENTE',
      institutionName: 'Banco', initialBalance: 100, estimatedBalance: 100,
      initialBalanceDate: '2026-01-01', active: true }]);
    fixture.detectChanges();
    await fixture.whenStable();
    await page.select(payable);
    page.walletId = 5;
    page.paidOn = '2026-11-20';
    const confirm = vi.spyOn(TestBed.inject(ActionConfirmation), 'ask').mockResolvedValue(true);
    fixture.detectChanges();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(element => element.textContent?.includes('Registrar pagamento integral'))!;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api['payPayable']).toHaveBeenCalledWith(3, 5, '2026-11-20');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Pagamento integral registrado');
    expect(confirm).toHaveBeenCalledWith('Confirmar pagamento',
      expect.stringMatching(/R\$\s+100,00/), 'Confirmar pagamento', false);
  });

  it('keeps the payment form and does not debit when confirmation is declined', async () => {
    const fixture = TestBed.createComponent(PayablesPage);
    const page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await page.select(payable);
    page.walletId = 5;
    page.paidOn = '2026-11-20';
    vi.spyOn(TestBed.inject(ActionConfirmation), 'ask').mockResolvedValue(false);

    await page.pay();

    expect(api['payPayable']).not.toHaveBeenCalled();
    expect(api['csrf']).not.toHaveBeenCalled();
    expect(page.walletId).toBe(5);
    expect(page.paidOn).toBe('2026-11-20');
  });

  it('does not suggest repeating a successful payment when refreshing the list fails', async () => {
    const fixture = TestBed.createComponent(PayablesPage);
    const page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    await page.select(payable);
    page.walletId = 5;
    page.paidOn = '2026-11-20';
    api['payables'].mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    vi.spyOn(TestBed.inject(ActionConfirmation), 'ask').mockResolvedValue(true);
    await page.pay();
    fixture.detectChanges();
    expect(api['payPayable']).toHaveBeenCalledTimes(1);
    expect(page.notice()).toContain('Pagamento integral registrado');
    expect(page.error()).toContain('operação foi concluída');
    expect(page.selected()).toBeNull();
    expect(page.loadFailed()).toBe(true);
  });
});
