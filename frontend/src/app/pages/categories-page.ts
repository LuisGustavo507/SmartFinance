import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';
import { Category } from '../api';
import { MaintenanceDialog } from '../maintenance-dialog';

@Component({
  selector: 'app-categories-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, MaintenanceDialog],
  templateUrl: './categories-page.html'
})
export class CategoriesPage {
  readonly store = inject(FinanceStore);

  editedCategory(): Category | undefined {
    return this.store.categories().find(category => category.id === this.store.editingCategoryId);
  }

  readonly categoryDirty = (): boolean => {
    const category = this.editedCategory();
    return !!category && (this.store.editingCategoryName !== category.name ||
      this.store.editingCategoryKind !== category.kind);
  };

  closeEditor(): void {
    this.store.editingCategoryId = null;
  }
}
