import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { CategoriesPage } from './pages/categories-page';
import { FinanceStore } from './finance-store';

describe('CategoriesPage', () => {
  it('shows inactive categories and allows toggling their state', () => {
    TestBed.configureTestingModule({
      imports: [CategoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(CategoriesPage);
    const store = TestBed.inject(FinanceStore);
    const active = { id: 1, ownerUserId: 1, name: 'Mercado',
      kind: 'DESPESA' as const, active: true };
    const inactive = { ...active, id: 2, name: 'Antiga', active: false };
    store.categories.set([active, inactive]);
    const toggle = vi.spyOn(store, 'setCategoryActive').mockResolvedValue(undefined);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Antiga');
    expect(page.textContent).toContain('inativa');
    const buttons = [...page.querySelectorAll('button')];
    buttons.find(button => button.textContent?.trim() === 'Desativar')?.click();
    buttons.find(button => button.textContent?.trim() === 'Reativar')?.click();
    expect(toggle).toHaveBeenCalledWith(active, false);
    expect(toggle).toHaveBeenCalledWith(inactive, true);
  });
});
