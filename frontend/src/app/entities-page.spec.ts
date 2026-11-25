import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EntitiesPage } from './pages/entities-page';
import { FinanceStore } from './finance-store';
import { vi } from 'vitest';

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

  it('shows a single accessible edit action and keeps inactive entities manageable', () => {
    TestBed.configureTestingModule({
      imports: [EntitiesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const fixture = TestBed.createComponent(EntitiesPage);
    const store = TestBed.inject(FinanceStore);
    const entity = { id: 5, ownerUserId: 1, name: 'Fornecedor', active: false };
    store.counterparties.set([entity]);
    const toggle = vi.spyOn(store, 'setCounterpartyActive').mockResolvedValue(undefined);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const row = page.querySelector('.entity-list li') as HTMLElement;
    expect(row.textContent).toContain('Inativa');
    expect(row.querySelectorAll('button')).toHaveLength(1);
    (row.querySelector('button[aria-label="Editar entidade Fornecedor"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const dialog = document.querySelector('.p-dialog') as HTMLElement;
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)).not.toBeNull();
    expect(dialog.textContent).toContain('Gerenciar entidade');
    expect(dialog.querySelector('label[for="entity-edit-name"]')).not.toBeNull();
    const reactivate = [...dialog.querySelectorAll<HTMLButtonElement>('button')]
      .find(button => button.textContent?.trim() === 'Reativar')!;
    reactivate.click();
    expect(toggle).toHaveBeenCalledWith(5, true);
    expect([...dialog.querySelectorAll<HTMLButtonElement>('.record-operations button')]
      .some(button => button.textContent?.trim() === 'Excluir')).toBe(true);
  });
});
