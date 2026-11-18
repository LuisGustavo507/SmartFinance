import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { FinanceStore } from '../finance-store';

@Component({
  selector: 'app-entities-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule],
  templateUrl: './entities-page.html'
})
export class EntitiesPage {
  readonly store = inject(FinanceStore);
}
