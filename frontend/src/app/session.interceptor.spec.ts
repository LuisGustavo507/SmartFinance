import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Api } from './api';
import { sessionInterceptor } from './session.interceptor';

describe('sessionInterceptor', () => {
  let api: Api;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting()
      ]
    });
    api = TestBed.inject(Api);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renews once for concurrent unauthorized reads, then retries both', async () => {
    const results: string[] = [];
    api.me().subscribe({ next: user => results.push(user.name), error: () => results.push('error') });
    api.wallets().subscribe({ next: () => results.push('wallets'), error: () => results.push('error') });
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/wallets').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(null);
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/auth/me').flush({ id: 1, name: 'Ana', email: 'ana@example.test' });
    http.expectOne('/api/wallets').flush([]);
    expect(results).toEqual(['Ana', 'wallets']);
  });

  it('never renews after a CSRF error', () => {
    let status = 0;
    api.createWallet('Dinheiro', 0, '2026-01-01').subscribe({
      error: error => status = error.status
    });
    http.expectOne('/api/wallets').flush(null, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });

  it('renews and retries a protected mutation once on 401', async () => {
    let created = false;
    api.createWallet('Dinheiro', 0, '2026-01-01').subscribe({
      next: () => created = true
    });
    http.expectOne('/api/wallets').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(null);
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/wallets').flush({ id: 3 });
    expect(created).toBe(true);
  });

  it('does not renew when login credentials are rejected', () => {
    let status = 0;
    api.login('unknown@example.test', 'wrong').subscribe({
      error: error => status = error.status
    });
    http.expectOne('/api/auth/login').flush(null,
      { status: 401, statusText: 'Unauthorized' });
    expect(status).toBe(401);
  });
});
