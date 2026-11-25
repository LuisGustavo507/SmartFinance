import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { Counterparty } from '../api';
import { MaintenanceDialog } from '../maintenance-dialog';

@Component({
  selector: 'app-entities-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, MaintenanceDialog],
  templateUrl: './entities-page.html'
})
export class EntitiesPage {
  readonly store = inject(FinanceStore);

  editedEntity(): Counterparty | undefined {
    return this.store.counterparties().find(entity => entity.id === this.store.editingEntityId);
  }

  readonly entityDirty = (): boolean => {
    const entity = this.editedEntity();
    return !!entity && this.store.editingEntityName !== entity.name;
  };

  closeEditor(): void {
    this.store.editingEntityId = null;
  }
}
