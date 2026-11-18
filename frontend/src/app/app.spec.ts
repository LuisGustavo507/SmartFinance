import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

const summary = (month: string) => ({
  month, realizedIncome: 1500, realizedExpenses: 400, realizedResult: 1100,
  plannedIncome: 0, plannedExpenses: 200, projectedResult: 900
});

describe('App shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();
  });

  it('obtains CSRF and shows login if there is no session', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigation = router.navigateByUrl('/');
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await navigation;
    await new Promise(resolve => setTimeout(resolve, 0));
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Entre na sua conta');
    expect(compiled.querySelector('aside')).toBeNull();
    expect(router.url).toBe('/login');
    http.verify();
  });

  it('shows the lateral menu and the home overview after authentication', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigation = router.navigateByUrl('/');
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/auth/csrf').flush(null);
    await Promise.resolve();
    http.expectOne('/api/auth/me').flush({ id: 1, name: 'Ana', email: 'ana@example.test' });
    await Promise.resolve();
    http.expectOne('/api/wallets').flush([
      { id: 3, ownerUserId: 1, name: 'Dinheiro', initialBalance: 100,
        estimatedBalance: 1100, initialBalanceDate: '2026-01-01', active: true, type: 'CARTEIRA_DINHEIRO' as const, institutionName: null }
    ]);
    http.expectOne('/api/categories').flush([]);
    http.expectOne('/api/entities').flush([]);
    http.expectOne(request => request.url === '/api/entries').flush([]);
    http.expectOne(request => request.url === '/api/monthly-summary')
      .flush(summary('2026-11-01'));
    http.expectOne(request => request.url === '/api/transfers').flush([]);
    http.expectOne('/api/cards').flush([]);
    http.expectOne('/api/institutions').flush([]);
    await navigation;
    await new Promise(resolve => setTimeout(resolve, 0));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const nav = compiled.querySelector('aside nav[aria-label="Páginas"]');
    expect(nav).not.toBeNull();
    expect(nav?.querySelector('a[href="/lancamentos"]')?.textContent).toContain('Lançamentos');
    expect(nav?.querySelector('a[href="/carteiras"]')?.textContent).toContain('Carteiras');
    expect(nav?.querySelector('a[href="/cartoes"]')?.textContent).toContain('Cartões');
    expect(compiled.querySelector('h1')?.textContent).toContain('Olá, Ana');
    expect(compiled.textContent).toContain('Saldo estimado total');
    expect(compiled.textContent).toMatch(/R\$\s1\.100,00/);
    http.verify();
  });
});
