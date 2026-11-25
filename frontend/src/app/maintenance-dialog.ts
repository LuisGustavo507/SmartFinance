import { Component, ElementRef, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { ActionConfirmation } from './action-confirmation';

@Component({
  selector: 'app-maintenance-dialog',
  imports: [DialogModule],
  template: `
    <p-dialog [visible]="visible" [modal]="true" [draggable]="false" [resizable]="false"
              [maximizable]="false" [closable]="false" [closeOnEscape]="false"
              [dismissableMask]="false" [focusOnShow]="false" [blockScroll]="true"
              [style]="{ width: 'min(' + maxWidth + ', calc(100vw - 2rem))' }"
              [contentStyle]="{ maxHeight: 'min(70dvh, 45rem)', overflowY: 'auto' }"
              (keydown.escape)="escape($event)" (onShow)="focusFirstField()"
              (onHide)="restoreFocus()">
      <ng-template #header let-titleId="ariaLabelledBy">
        <div class="maintenance-heading">
          <strong [id]="titleId">{{ dialogTitle }}</strong>
          <button type="button" class="maintenance-close" aria-label="Fechar"
                  [disabled]="busy" (click)="requestClose()">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
        </div>
      </ng-template>
      <ng-content />
    </p-dialog>
  `,
  styles: [`
    .maintenance-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; width: 100%; }
    .maintenance-close {
      display: inline-grid; place-items: center; flex: 0 0 44px;
      width: 44px; height: 44px; border: 0; border-radius: .5rem;
      background: transparent; color: var(--sf-ink); cursor: pointer;
    }
    .maintenance-close:hover { background: var(--sf-sky); }
    .maintenance-close:disabled { opacity: .5; cursor: default; }
  `]
})
export class MaintenanceDialog implements OnChanges {
  private readonly confirmation = inject(ActionConfirmation);
  private readonly element: ElementRef<HTMLElement> = inject(ElementRef);
  private trigger: HTMLElement | null = null;
  @Input() dialogTitle = '';
  @Input() visible = false;
  @Input() hasUnsavedChanges: () => boolean = () => false;
  @Input() busy = false;
  @Input() maxWidth = '28rem';
  @Input() fallbackFocusSelector = '';
  @Output() readonly closeRequested = new EventEmitter<void>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue && !changes['visible'].previousValue) {
      this.trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
  }

  async requestClose(): Promise<void> {
    if (this.busy || !this.visible) return;
    if (this.hasUnsavedChanges() && !await this.confirmation.ask('Descartar alterações',
      'Descartar as alterações não salvas?', 'Descartar')) return;
    this.closeRequested.emit();
  }

  escape(event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    void this.requestClose();
  }

  focusFirstField(): void {
    queueMicrotask(() => {
      const host = this.element.nativeElement;
      (host.querySelector<HTMLElement>(
        'input:not([disabled]), select:not([disabled]), textarea:not([disabled])'
      ) ?? host.querySelector<HTMLElement>('button.maintenance-close'))?.focus();
    });
  }

  restoreFocus(): void {
    const trigger = this.trigger;
    this.trigger = null;
    queueMicrotask(() => {
      if (trigger?.isConnected) {
        trigger.focus();
        return;
      }
      const fallback =
        (this.fallbackFocusSelector
          ? document.querySelector<HTMLElement>(this.fallbackFocusSelector)
          : null)
        ?? document.querySelector<HTMLElement>('main [tabindex="0"]')
        ?? document.querySelector<HTMLElement>('main');
      fallback?.focus();
    });
  }
}
