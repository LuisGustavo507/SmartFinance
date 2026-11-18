import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';

@Component({
  selector: 'app-categories-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule],
  templateUrl: './categories-page.html'
})
export class CategoriesPage {
  readonly store = inject(FinanceStore);
}
