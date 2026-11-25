import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { CategoriesPage } from './pages/categories-page';
import { FinanceStore } from './finance-store';
import { ActionConfirmation } from './action-confirmation';
import { MaintenanceDialog } from './maintenance-dialog';
import { By } from '@angular/platform-browser';

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
    const edit = [...page.querySelectorAll<HTMLButtonElement>('button[aria-label^="Editar categoria"]')];
    expect(edit).toHaveLength(2);
    expect(page.querySelector('li button[aria-label="Editar categoria Mercado"]')).not.toBeNull();
    edit[0].click();
    fixture.detectChanges();
    [...document.querySelectorAll('.p-dialog button')].find(button =>
      button.textContent?.trim() === 'Desativar')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    store.editingCategoryId = null;
    fixture.detectChanges();
    edit[1].click();
    fixture.detectChanges();
    [...document.querySelectorAll('.p-dialog button')].find(button =>
      button.textContent?.trim() === 'Reativar')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
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

  it('opens an accessible modal with edit fields and protected operations', () => {
    TestBed.configureTestingModule({
      imports: [CategoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(CategoriesPage);
    const store = TestBed.inject(FinanceStore);
    const category = { id: 1, ownerUserId: 1, name: 'Mercado',
      kind: 'DESPESA' as const, active: true };
    store.categories.set([category]);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    (page.querySelector('li button[aria-label="Editar categoria Mercado"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const form = document.querySelector('.p-dialog form') as HTMLFormElement;
    expect(form).not.toBeNull();
    expect(form.querySelector('label[for="category-edit-name"]')?.textContent).toContain('Nome');
    expect(form.querySelector('label[for="category-edit-kind"]')?.textContent).toContain('Natureza');
    expect(form.querySelector('input')?.parentElement).toBe(form);
    expect(form.querySelector('select')?.parentElement).toBe(form);
    expect([...form.querySelectorAll('.form-actions button')].map(button =>
      button.textContent?.trim())).toEqual(['Cancelar', 'Salvar']);
    expect(document.querySelector('.p-dialog button[aria-label="Fechar"]')).not.toBeNull();
    const dialog = document.querySelector('.p-dialog') as HTMLElement;
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)).not.toBeNull();
    expect([...document.querySelectorAll('.p-dialog .record-operations button')].map(button =>
      button.textContent?.trim())).toEqual(['Desativar', 'Excluir']);
  });

  it('asks before discarding a modified category and preserves the draft when declined', async () => {
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
    store.editingCategoryName = 'Rascunho';
    const dialog = fixture.debugElement.query(By.directive(MaintenanceDialog))
      .componentInstance as MaintenanceDialog;
    const ask = vi.spyOn(TestBed.inject(ActionConfirmation), 'ask').mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);
    await dialog.requestClose();
    expect(store.editingCategoryId).toBe(1);
    expect(store.editingCategoryName).toBe('Rascunho');
    await dialog.requestClose();
    expect(store.editingCategoryId).toBeNull();
    expect(ask).toHaveBeenCalledWith('Descartar alterações',
      'Descartar as alterações não salvas?', 'Descartar');
  });
});
