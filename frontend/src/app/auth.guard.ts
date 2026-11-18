import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { FinanceStore } from './finance-store';

export const authGuard: CanActivateFn = async () => {
  const store = inject(FinanceStore);
  const router = inject(Router);
  await store.initialize();
  return store.profile() ? true : router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = async () => {
  const store = inject(FinanceStore);
  const router = inject(Router);
  await store.initialize();
  return store.profile() ? router.createUrlTree(['/']) : true;
};
