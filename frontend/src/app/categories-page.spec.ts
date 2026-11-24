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

  it('keeps a long category list inside a focusable scroll area', () => {
    TestBed.configureTestingModule({
      imports: [CategoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(CategoriesPage);
    const store = TestBed.inject(FinanceStore);
    store.categories.set(Array.from({ length: 40 }, (_, index) => ({
      id: index + 1, ownerUserId: 1, name: `Categoria ${index + 1}`,
      kind: 'DESPESA' as const, active: true
    })));
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const list = page.querySelector('ul.scroll-area') as HTMLElement | null;
    expect(list).not.toBeNull();
    expect(list!.tabIndex).toBe(0);
    expect(page.textContent).toContain('Categoria 40');
  });

  it('keeps edit labels and actions grouped in a full-width form', () => {
    TestBed.configureTestingModule({
      imports: [CategoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(CategoriesPage);
    const store = TestBed.inject(FinanceStore);
    const category = { id: 1, ownerUserId: 1, name: 'Mercado',
      kind: 'DESPESA' as const, active: true };
    store.categories.set([category]);
    store.startCategoryEdit(category);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const form = page.querySelector('li form.inline-form') as HTMLFormElement;
    expect(form).not.toBeNull();
    expect(form.querySelector('label[for="category-rename-1"]')?.textContent).toContain('Nome');
    expect(form.querySelector('label[for="category-kind-1"]')?.textContent).toContain('Natureza');
    expect(form.querySelector('input')?.parentElement).toBe(form);
    expect(form.querySelector('select')?.parentElement).toBe(form);
    expect([...form.querySelectorAll('.form-actions button')].map(button =>
      button.textContent?.trim())).toEqual(['Cancelar', 'Salvar']);
  });
});
