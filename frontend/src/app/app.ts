import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FinanceStore } from './finance-store';
import { PrimeNG } from 'primeng/config';
import { setBrazilianLocale } from './locale';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly store = inject(FinanceStore);

  constructor() {
    setBrazilianLocale(inject(PrimeNG));
  }
}
