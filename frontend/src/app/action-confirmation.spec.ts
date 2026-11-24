import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ActionConfirmation } from './action-confirmation';

@Component({
  imports: [ConfirmDialogModule],
  providers: [{ provide: ConfirmationService, useExisting: ActionConfirmation }],
  template: `
    <p-confirmdialog key="finance-actions" closeAriaLabel="Fechar confirmação"
                     (onHide)="confirmation.restoreFocus()" />
    <button type="button" (click)="request()">Excluir entidade</button>
  `
})
class ConfirmationHost {
  readonly confirmation = inject(ActionConfirmation);
  result: boolean | null = null;

  async request(): Promise<void> {
    this.result = await this.confirmation.ask('Excluir entidade',
      'Excluir a entidade “Mercado”? Esta ação não pode ser desfeita.', 'Excluir entidade');
  }
}

describe('ActionConfirmation', () => {
  it('shows a PrimeNG dialog and does nothing when the user goes back', async () => {
    const fixture = TestBed.createComponent(ConfirmationHost);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    trigger.focus();
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const dialog = document.querySelector('.p-confirmdialog') as HTMLElement;
    expect(dialog).not.toBeNull();
    expect(dialog.getAttribute('role')).toBe('alertdialog');
    expect(dialog.textContent).toContain('Mercado');
    const back = [...dialog.querySelectorAll('button')]
      .find(button => button.textContent?.trim() === 'Voltar')!;
    back.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.result).toBe(false);
    expect(document.activeElement).toBe(trigger);
    fixture.destroy();
  });

  it('only accepts after clicking the explicit action', async () => {
    const fixture = TestBed.createComponent(ConfirmationHost);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.result).toBeNull();
    const dialog = document.querySelector('.p-confirmdialog') as HTMLElement;
    const accept = [...dialog.querySelectorAll('button')]
      .find(button => button.textContent?.trim() === 'Excluir entidade')!;
    accept.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.result).toBe(true);
    fixture.destroy();
  });

  it('dismisses an unfinished confirmation when navigation closes the view', async () => {
    const fixture = TestBed.createComponent(ConfirmationHost);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.confirmation.cancel();
    await fixture.whenStable();
    expect(fixture.componentInstance.result).toBe(false);
    fixture.destroy();
  });
});
