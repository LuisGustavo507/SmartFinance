import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FinanceStore } from './finance-store';
import { HomePage } from './pages/home-page';

describe('HomePage', () => {
  it('keeps the monthly overview without category and necessity breakdowns', async () => {
    await TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(HomePage);
    const store = TestBed.inject(FinanceStore);
    store.monthlySummary.set({
      month: '2026-11-01', realizedIncome: 100, realizedExpenses: 40,
      realizedResult: 60, plannedIncome: 150, plannedExpenses: 20,
      pendingExpenses: 30, projectedResult: 160,
      categoryTotals: [
        { categoryId: 1, categoryName: 'Aluguel', kind: 'DESPESA',
          realizedIncome: 0, realizedExpenses: 40, plannedIncome: 0, plannedExpenses: 20 }
      ],
      necessityTotals: [
        { necessity: 'NECESSARIA', realizedExpenses: 40, plannedExpenses: 20 }
      ]
    });
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Últimos lançamentos');
    expect(page.textContent).toContain('Resumo de');
    expect(page.textContent).toContain('Resultado projetado');
    expect(page.textContent).toContain('Resultado efetivado');
    expect(page.textContent).toContain('Previsto / a pagar');
    expect(page.textContent).toContain('Contas a pagar pendentes');
    const projection = [...page.querySelectorAll('.stat')]
      .find(stat => stat.textContent?.includes('Resultado projetado'))!;
    expect(projection.textContent).toMatch(/R\$\s160,00/);
    const groups = [...page.querySelectorAll('.summary-list .total strong')];
    expect(groups.map(group => group.textContent)).toEqual([
      expect.stringMatching(/R\$\s60,00/), expect.stringMatching(/R\$\s100,00/)
    ]);
    expect(page.textContent).not.toContain('Por categoria');
    expect(page.textContent).not.toContain('Despesas por necessidade');
    expect(page.textContent).not.toContain('Aluguel');
  });
});
