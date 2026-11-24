import { Injectable } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

@Injectable({ providedIn: 'root' })
export class ActionConfirmation extends ConfirmationService {
  private pending = false;
  private trigger: HTMLElement | null = null;
  private resolvePending: ((accepted: boolean) => void) | null = null;

  ask(header: string, message: string, acceptLabel: string, danger = true): Promise<boolean> {
    if (this.pending) return Promise.resolve(false);
    this.pending = true;
    this.trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return new Promise(resolve => {
      this.resolvePending = resolve;
      this.confirm({
        key: 'finance-actions',
        header,
        message,
        icon: danger ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle',
        acceptLabel,
        rejectLabel: 'Voltar',
        acceptButtonProps: { severity: danger ? 'danger' : 'primary' },
        rejectButtonProps: { severity: 'secondary', outlined: true },
        defaultFocus: 'reject',
        closeOnEscape: true,
        dismissableMask: false,
        accept: () => this.finish(true),
        reject: () => this.finish(false)
      });
    });
  }

  cancel(): void {
    if (!this.pending) return;
    this.close();
    this.finish(false);
  }

  restoreFocus(): void {
    const trigger = this.trigger;
    this.trigger = null;
    queueMicrotask(() => {
      if (trigger?.isConnected) trigger.focus();
    });
  }

  private finish(accepted: boolean): void {
    this.pending = false;
    this.resolvePending?.(accepted);
    this.resolvePending = null;
  }
}
