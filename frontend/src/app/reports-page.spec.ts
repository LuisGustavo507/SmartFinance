import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReportsPage } from './pages/reports-page';
import { FinanceStore } from './finance-store';
import { vi } from 'vitest';

describe('ReportsPage', () => {
  it('consults the entity period report for the chosen period', async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const fixture = TestBed.createComponent(ReportsPage);
    const store = TestBed.inject(FinanceStore);
    const http = TestBed.inject(HttpTestingController);
    store.reportFromMonth = '2026-01';
    store.reportToMonth = '2026-03';

    const loading = store.loadEntityPeriodReport();
    const request = http.expectOne(candidate =>
      candidate.url === '/api/reports/entity-period' &&
      candidate.params.get('from') === '2026-01' &&
      candidate.params.get('to') === '2026-03');
    request.flush({
      from: '2026-01-01', to: '2026-03-31',
      entities: [
        { entityId: 5, entityName: 'Distribuidora', realizedIncome: 200,
          realizedExpenses: 110, plannedIncome: 0, plannedExpenses: 0 },
        { entityId: null, entityName: 'Sem entidade', realizedIncome: 0,
          realizedExpenses: 5, plannedIncome: 0, plannedExpenses: 0 }
      ]
    });
    http.expectOne(candidate => candidate.url === '/api/reports/monthly').flush([
      { month: '2026-01-01', realizedIncome: 200, realizedExpenses: 110,
        plannedIncome: 0, plannedExpenses: 0, realizedResult: 90, projectedResult: 90 },
      { month: '2026-02-01', realizedIncome: 0, realizedExpenses: 0,
        plannedIncome: 0, plannedExpenses: 0, realizedResult: 0, projectedResult: 0 },
      { month: '2026-03-01', realizedIncome: 0, realizedExpenses: 5,
        plannedIncome: 0, plannedExpenses: 0, realizedResult: -5, projectedResult: -5 }
    ]);
    await loading;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Distribuidora');
    expect(page.textContent).toContain('Sem entidade');
    expect(page.textContent).toMatch(/R\$\s200,00/);
    expect(page.textContent).toMatch(/R\$\s110,00/);
    expect(page.textContent).toContain('Movimento ao longo do tempo');
    const bars = page.querySelectorAll('.chart-income');
    const expenses = page.querySelectorAll('.chart-expense');
    expect(bars.length).toBe(3);
    expect(expenses.length).toBe(3);
    expect(page.querySelector('.chart')?.getAttribute('aria-label'))
      .toContain('2026-01');
    http.verify();
  });

  it('refuses an inverted period without calling the API', async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const fixture = TestBed.createComponent(ReportsPage);
    const store = TestBed.inject(FinanceStore);
    const http = TestBed.inject(HttpTestingController);
    store.reportFromMonth = '2026-03';
    store.reportToMonth = '2026-01';
    const spy = vi.spyOn(store, 'loadEntityPeriodReport');

    await store.loadEntityPeriodReport();
    expect(store.error()).toContain('início do período');
    http.expectNone('/api/reports/entity-period');
    spy.mockRestore();
    http.verify();
  });
});
