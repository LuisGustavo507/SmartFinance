import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EntitiesPage } from './pages/entities-page';
import { FinanceStore } from './finance-store';

describe('EntitiesPage', () => {
  it('keeps a long entity list inside a focusable scroll area', () => {
    TestBed.configureTestingModule({
      imports: [EntitiesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(EntitiesPage);
    const store = TestBed.inject(FinanceStore);
    store.counterparties.set(Array.from({ length: 40 }, (_, index) => ({
      id: index + 1, ownerUserId: 1, name: `Entidade ${index + 1}`, active: true
    })));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const list = page.querySelector('ul.scroll-area') as HTMLElement | null;
    expect(list).not.toBeNull();
    expect(list!.tabIndex).toBe(0);
    expect(page.textContent).toContain('Entidade 40');
  });
});
