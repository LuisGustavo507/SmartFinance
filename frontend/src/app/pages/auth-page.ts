import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { FinanceStore } from '../finance-store';

@Component({
  selector: 'app-auth-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, PasswordModule],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.css'
})
export class AuthPage {
  readonly store = inject(FinanceStore);
  private readonly router = inject(Router);

  async submit(): Promise<void> {
    await this.store.submitAccount();
    if (this.store.profile()) {
      await this.router.navigateByUrl('/');
    }
  }
}
