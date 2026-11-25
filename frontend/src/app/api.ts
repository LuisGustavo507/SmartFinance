import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface Profile {
  id: number;
  name: string;
  email: string;
}

export type WalletType = 'CONTA_CORRENTE' | 'POUVANCA' | 'CONTA_PAGAMENTO'
  | 'CARTEIRA_DINHEIRO' | 'OUTRA';

export interface Wallet {
  id: number;
  ownerUserId: number;
  name: string;
  type: WalletType;
  institutionName: string | null;
  initialBalance: number;
  initialBalanceDate: string;
  active: boolean;
  estimatedBalance: number;
}

export interface Institution {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export type CategoryKind = 'RECEITA' | 'DESPESA';

export interface Category {
  id: number;
  ownerUserId: number;
  name: string;
  kind: CategoryKind;
  active: boolean;
}

export interface Counterparty {
  id: number;
  ownerUserId: number;
  name: string;
  active: boolean;
}

export interface Entry {
  id: number;
  ownerUserId: number;
  walletId: number;
  categoryId: number;
  kind: CategoryKind;
  description: string;
  amount: number;
  occurredOn: string | null;
  expectedOn: string | null;
  referenceMonth: string;
  status: 'EFETIVADO' | 'PREVISTO' | 'CANCELADO';
  createdAt: string;
  effectiveAt: string | null;
  entityId: number | null;
  entityName: string | null;
  seriesId: number | null;
  occurrenceNumber: number | null;
  totalOccurrences: number | null;
  necessity: Necessity | null;
}

export interface MonthlySummary {
  month: string;
  realizedIncome: number;
  realizedExpenses: number;
  realizedResult: number;
  plannedIncome: number;
  plannedExpenses: number;
  pendingExpenses?: number;
  projectedResult: number;
  reversalCreditsGenerated?: number;
  reversalCreditsApplied?: number;
  necessityTotals?: NecessityTotal[];
  categoryTotals?: CategoryTotal[];
}

export interface CategoryTotal {
  categoryId: number;
  categoryName: string;
  kind: CategoryKind;
  realizedIncome: number;
  realizedExpenses: number;
  plannedIncome: number;
  plannedExpenses: number;
  pendingExpenses?: number;
}

export interface EntityPeriodTotal {
  entityId: number | null;
  entityName: string;
  realizedIncome: number;
  realizedExpenses: number;
  plannedIncome: number;
  plannedExpenses: number;
  pendingExpenses?: number;
}

export interface EntityPeriodReport {
  from: string;
  to: string;
  entities: EntityPeriodTotal[];
}

export interface MonthlyPoint {
  month: string;
  realizedIncome: number;
  realizedExpenses: number;
  plannedIncome: number;
  plannedExpenses: number;
  pendingExpenses?: number;
  realizedResult: number;
  projectedResult: number;
}

export interface NecessityTotal {
  necessity: Necessity | null;
  realizedExpenses: number;
  plannedExpenses: number;
  pendingExpenses?: number;
}

export type PayableStatus = 'PENDENTE' | 'ATRASADA' | 'QUITADA' | 'CANCELADA';

export interface Payable {
  id: number;
  ownerUserId: number;
  categoryId: number;
  categoryName: string;
  entityId: number | null;
  entityName: string | null;
  description: string;
  necessity: Necessity;
  amount: number;
  dueOn: string;
  status: PayableStatus;
  createdAt: string;
}

export interface PayablePayment {
  id: number;
  ownerUserId: number;
  payableId: number;
  walletId: number;
  amount: number;
  paidOn: string;
  createdAt: string;
  reversedAt: string | null;
  reversalReason: string | null;
}

export interface Transfer {
  id: number;
  ownerUserId: number;
  sourceWalletId: number;
  destinationWalletId: number;
  amount: number;
  description: string | null;
  transferredAt: string;
  createdAt: string;
}

export interface CreditCard {
  id: number;
  ownerUserId: number;
  name: string;
  institutionName: string | null;
  closingDay: number;
  dueDay: number;
  creditLimit: number | null;
  defaultPaymentWalletId: number | null;
  active: boolean;
}

export type Necessity = 'NECESSARIA' | 'NEUTRA' | 'FUTIL';

export type CardInstallmentStatus = 'ATIVA' | 'CANCELADA' | 'ESTORNADA';

export interface CardInstallment {
  id: number;
  purchaseId: number;
  statementId: number;
  number: number;
  amount: number;
  status: CardInstallmentStatus;
  description: string;
  categoryId: number;
  categoryName: string;
  entityId: number | null;
  entityName: string | null;
  statementDueDate: string;
  statementReferenceMonth: string;
}

export interface CardPurchase {
  id: number;
  ownerUserId: number;
  cardId: number;
  categoryId: number;
  entityId: number | null;
  description: string;
  totalAmount: number;
  purchasedOn: string;
  installmentCount: number;
  necessity: Necessity;
  status: CardInstallmentStatus;
  installments: CardInstallment[];
}

export type CardStatementStatus = 'PROJETADA' | 'ABERTA' | 'FECHADA' | 'PAGA' | 'ATRASADA';

export interface CardStatement {
  id: number;
  ownerUserId: number;
  cardId: number;
  referenceMonth: string;
  closingDate: string;
  dueDate: string;
  status: CardStatementStatus;
  totalAmount: number;
  appliedCredits: number;
  projectedCredits: number;
  closedAmount: number | null;
  paidAmount: number;
  paidOn: string | null;
  installments: CardInstallment[];
}

export interface CardPayment {
  id: number;
  ownerUserId: number;
  statementId: number;
  walletId: number;
  amount: number;
  paidOn: string;
  createdAt: string;
}

export interface ReversalCredit {
  id: number;
  purchaseId: number;
  cardId: number;
  originalAmount: number;
  availableAmount: number;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  csrf(): Observable<void> {
    return this.http.get<void>('/api/auth/csrf');
  }

  me(): Observable<Profile> {
    return this.http.get<Profile>('/api/auth/me');
  }

  register(name: string, email: string, password: string): Observable<Profile> {
    return this.http.post<Profile>('/api/auth/register', { name, email, password });
  }

  login(email: string, password: string): Observable<void> {
    return this.http.post<void>('/api/auth/login', { email, password });
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {});
  }

  wallets(): Observable<Wallet[]> {
    return this.http.get<Wallet[]>('/api/wallets');
  }

  createWallet(name: string, initialBalance: number, initialBalanceDate: string,
    type: WalletType = 'CARTEIRA_DINHEIRO', institutionName: string | null = null): Observable<Wallet> {
    return this.http.post<Wallet>('/api/wallets', {
      name, initialBalance, initialBalanceDate, type, institutionName
    });
  }

  institutions(): Observable<Institution[]> {
    return this.http.get<Institution[]>('/api/institutions');
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  createCategory(name: string, kind: CategoryKind): Observable<Category> {
    return this.http.post<Category>('/api/categories', { name, kind });
  }

  setCategoryActive(id: number, active: boolean): Observable<Category> {
    return this.http.patch<Category>(`/api/categories/${id}/status`, { active });
  }

  updateCategory(id: number, name: string, kind: CategoryKind): Observable<Category> {
    return this.http.put<Category>(`/api/categories/${id}`, { name, kind });
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`/api/categories/${id}`);
  }

  entries(month: string, entityId: number | null = null): Observable<Entry[]> {
    return this.http.get<Entry[]>('/api/entries', {
      params: entityId === null ? { month } : { month, entityId }
    });
  }

  createEntry(walletId: number, categoryId: number, description: string,
    amount: number, occurredOn: string, entityId: number | null,
    necessity: Necessity | null, newEntityName: string | null): Observable<Entry> {
    return this.http.post<Entry>('/api/entries', {
      walletId, categoryId, description, amount, occurredOn, entityId, necessity, newEntityName
    });
  }

  planEntry(walletId: number, categoryId: number, description: string,
    amount: number, expectedOn: string, entityId: number | null,
    necessity: Necessity | null, newEntityName: string | null): Observable<Entry> {
    return this.http.post<Entry>('/api/entries/planned', {
      walletId, categoryId, description, amount, expectedOn, entityId, necessity, newEntityName
    });
  }

  createSeries(walletId: number, categoryId: number, description: string,
    amount: number, firstExpectedOn: string, totalOccurrences: number,
    entityId: number | null, necessity: Necessity | null,
    newEntityName: string | null): Observable<Entry[]> {
    return this.http.post<Entry[]>('/api/entries/series', {
      walletId, categoryId, description, amount, firstExpectedOn,
      totalOccurrences, entityId, necessity, newEntityName
    });
  }

  series(id: number): Observable<Entry[]> {
    return this.http.get<Entry[]>(`/api/entries/series/${id}`);
  }

  updateEntry(id: number, walletId: number, categoryId: number, description: string,
    amount: number, date: string, status: 'PREVISTO' | 'EFETIVADO',
    entityId: number | null, necessity: Necessity | null): Observable<Entry> {
    return this.http.put<Entry>(`/api/entries/${id}`, {
      walletId, categoryId, description, amount,
      ...(status === 'PREVISTO' ? { expectedOn: date } : { occurredOn: date }),
      entityId, necessity
    });
  }

  updateSeriesFrom(seriesId: number, occurrenceNumber: number, walletId: number,
    categoryId: number, description: string, amount: number, expectedOn: string,
    entityId: number | null, necessity: Necessity | null): Observable<Entry[]> {
    return this.http.put<Entry[]>(
      `/api/entries/series/${seriesId}/occurrences/${occurrenceNumber}`,
      { walletId, categoryId, description, amount, expectedOn, entityId, necessity });
  }

  cancelEntry(id: number): Observable<void> {
    return this.http.post<void>(`/api/entries/${id}/cancel`, {});
  }

  payables(month: string): Observable<Payable[]> {
    return this.http.get<Payable[]>('/api/payables', { params: { month } });
  }

  payable(id: number): Observable<Payable> {
    return this.http.get<Payable>(`/api/payables/${id}`);
  }

  createPayable(data: {
    categoryId: number; entityId: number | null; description: string;
    necessity: Necessity; amount: number; dueOn: string
  }): Observable<Payable> {
    return this.http.post<Payable>('/api/payables', data);
  }

  updatePayable(id: number, data: {
    categoryId: number; entityId: number | null; description: string;
    necessity: Necessity; amount: number; dueOn: string
  }): Observable<Payable> {
    return this.http.put<Payable>(`/api/payables/${id}`, data);
  }

  cancelPayable(id: number): Observable<void> {
    return this.http.post<void>(`/api/payables/${id}/cancel`, {});
  }

  payablePayments(id: number): Observable<PayablePayment[]> {
    return this.http.get<PayablePayment[]>(`/api/payables/${id}/payments`);
  }

  payPayable(id: number, walletId: number, paidOn: string): Observable<PayablePayment> {
    return this.http.post<PayablePayment>(`/api/payables/${id}/payments`, { walletId, paidOn });
  }

  reversePayablePayment(id: number, paymentId: number, reason: string): Observable<PayablePayment> {
    return this.http.post<PayablePayment>(
      `/api/payables/${id}/payments/${paymentId}/reverse`, { reason });
  }

  updateEntryEntity(id: number, entityId: number | null): Observable<Entry> {
    return this.http.put<Entry>(`/api/entries/${id}/entity`, { entityId });
  }

  updateCardPurchaseEntity(purchaseId: number, entityId: number | null): Observable<CardPurchase> {
    return this.http.put<CardPurchase>(`/api/card-purchases/${purchaseId}/entity`, { entityId });
  }

  counterparties(): Observable<Counterparty[]> {
    return this.http.get<Counterparty[]>('/api/entities');
  }

  createCounterparty(name: string): Observable<Counterparty> {
    return this.http.post<Counterparty>('/api/entities', { name });
  }

  setCounterpartyActive(id: number, active: boolean): Observable<Counterparty> {
    return this.http.patch<Counterparty>(`/api/entities/${id}/status`, { active });
  }

  renameCounterparty(id: number, name: string): Observable<Counterparty> {
    return this.http.put<Counterparty>(`/api/entities/${id}`, { name });
  }

  deleteCounterparty(id: number): Observable<void> {
    return this.http.delete<void>(`/api/entities/${id}`);
  }

  monthlySummary(month: string, entityId: number | null = null): Observable<MonthlySummary> {
    return this.http.get<MonthlySummary>('/api/monthly-summary', {
      params: entityId === null ? { month } : { month, entityId }
    });
  }

  entityPeriodReport(from: string, to: string): Observable<EntityPeriodReport> {
    return this.http.get<EntityPeriodReport>('/api/reports/entity-period', {
      params: { from, to }
    });
  }

  monthlyReport(from: string, to: string): Observable<MonthlyPoint[]> {
    return this.http.get<MonthlyPoint[]>('/api/reports/monthly', { params: { from, to } });
  }

  transfers(month: string): Observable<Transfer[]> {
    return this.http.get<Transfer[]>('/api/transfers', { params: { month } });
  }

  createTransfer(sourceWalletId: number, destinationWalletId: number, amount: number,
    description: string, transferredAt: string): Observable<Transfer> {
    return this.http.post<Transfer>('/api/transfers', {
      sourceWalletId, destinationWalletId, amount, description, transferredAt
    });
  }

  cards(): Observable<CreditCard[]> {
    return this.http.get<CreditCard[]>('/api/cards');
  }

  createCard(name: string, institutionName: string, closingDay: number, dueDay: number,
    creditLimit: number | null, defaultPaymentWalletId: number | null): Observable<CreditCard> {
    return this.http.post<CreditCard>('/api/cards', {
      name, institutionName, closingDay, dueDay, creditLimit, defaultPaymentWalletId
    });
  }

  updateCard(id: number, name: string, institutionName: string, closingDay: number,
    dueDay: number, creditLimit: number | null,
    defaultPaymentWalletId: number | null): Observable<CreditCard> {
    return this.http.put<CreditCard>(`/api/cards/${id}`, {
      name, institutionName, closingDay, dueDay, creditLimit, defaultPaymentWalletId
    });
  }

  setCardActive(id: number, active: boolean): Observable<CreditCard> {
    return this.http.patch<CreditCard>(`/api/cards/${id}/status`, { active });
  }

  cardStatements(month: string): Observable<CardStatement[]> {
    return this.http.get<CardStatement[]>('/api/card-statements', { params: { month } });
  }

  createCardPurchase(cardId: number, categoryId: number, description: string, amount: number,
    purchasedOn: string, installments: number, entityId: number | null,
    necessity: Necessity, newEntityName: string | null): Observable<CardPurchase> {
    return this.http.post<CardPurchase>('/api/card-purchases', {
      cardId, categoryId, description, amount, purchasedOn, installments, entityId, necessity,
      newEntityName
    });
  }

  payCardStatement(statementId: number, walletId: number, paidOn: string): Observable<CardPayment> {
    return this.http.post<CardPayment>(`/api/card-statements/${statementId}/payments`, {
      walletId, paidOn
    });
  }

  reverseCardPurchase(purchaseId: number): Observable<CardPurchase> {
    return this.http.post<CardPurchase>(`/api/card-purchases/${purchaseId}/reversal`, {});
  }

  cardCredits(month: string): Observable<ReversalCredit[]> {
    return this.http.get<ReversalCredit[]>('/api/card-credits', { params: { month } });
  }
}
