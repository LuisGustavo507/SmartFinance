import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { Shell } from './shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Entrar · Smart Finance',
    loadComponent: () => import('./pages/auth-page').then(page => page.AuthPage)
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        title: 'Início · Smart Finance',
        loadComponent: () => import('./pages/home-page').then(page => page.HomePage)
      },
      {
        path: 'carteiras',
        title: 'Carteiras · Smart Finance',
        loadComponent: () => import('./pages/wallets-page').then(page => page.WalletsPage)
      },
      {
        path: 'lancamentos',
        title: 'Lançamentos · Smart Finance',
        loadComponent: () => import('./pages/entries-page').then(page => page.EntriesPage)
      },
      {
        path: 'contas-a-pagar',
        title: 'Contas a pagar · Smart Finance',
        loadComponent: () => import('./pages/payables-page').then(page => page.PayablesPage)
      },
      {
        path: 'cartoes',
        title: 'Cartões · Smart Finance',
        loadComponent: () => import('./pages/cards-page').then(page => page.CardsPage)
      },
      {
        path: 'faturas',
        title: 'Faturas · Smart Finance',
        loadComponent: () => import('./pages/statements-page').then(page => page.StatementsPage)
      },
      {
        path: 'transferencias',
        title: 'Transferências · Smart Finance',
        loadComponent: () => import('./pages/transfers-page').then(page => page.TransfersPage)
      },
      {
        path: 'categorias',
        title: 'Categorias · Smart Finance',
        loadComponent: () => import('./pages/categories-page').then(page => page.CategoriesPage)
      },
      {
        path: 'entidades',
        title: 'Entidades · Smart Finance',
        loadComponent: () => import('./pages/entities-page').then(page => page.EntitiesPage)
      },
      {
        path: 'relatorios',
        title: 'Relatórios · Smart Finance',
        loadComponent: () => import('./pages/reports-page').then(page => page.ReportsPage)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
