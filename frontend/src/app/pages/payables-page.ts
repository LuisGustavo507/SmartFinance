import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { Api, Necessity, Payable, PayablePayment } from '../api';
import { FinanceStore } from '../finance-store';
import { money, monthLabel, shortDate } from '../format';
import { DateField } from '../date-field';
import { MoneyField } from '../money-field';

@Component({
  selector: 'app-payables-page',
  imports: [FormsModule, ButtonModule, CardModule, InputTextModule, DateField, MoneyField],
  templateUrl: './payables-page.html',
  styleUrl: './payables-page.css'
})
export class PayablesPage implements OnInit {
  readonly store = inject(FinanceStore);
  private readonly api = inject(Api);
  readonly money = money;
  readonly monthLabel = monthLabel;
  readonly shortDate = shortDate;
  readonly payables = signal<Payable[]>([]);
  readonly selected = signal<Payable | null>(null);
  readonly payments = signal<PayablePayment[]>([]);
  readonly paymentsLoading = signal(false);
  readonly paymentsLoadFailed = signal(false);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  month = this.store.selectedMonth;
  categoryId: number | null = null;
  entityId: number | null = null;
  necessity: Necessity | null = null;
  description = '';
  amount: number | null = null;
  dueOn = '';
  editingId: number | null = null;
  walletId: number | null = null;
  paidOn = new Date().toISOString().slice(0, 10);
  reversalReason = '';

  ngOnInit(): void {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    try {
      const items = await firstValueFrom(this.api.payables(this.month));
      this.payables.set(items);
      if (this.selected()) {
        const selectedId = this.selected()!.id;
        this.paymentsLoading.set(true);
        const [detail, history] = await Promise.all([
          firstValueFrom(this.api.payable(selectedId)),
          firstValueFrom(this.api.payablePayments(selectedId))
        ]);
        this.selected.set(detail);
        this.payments.set(history);
        this.paymentsLoadFailed.set(false);
      }
    } catch (error) {
      this.loadFailed.set(true);
      if (this.paymentsLoading()) this.paymentsLoadFailed.set(true);
      this.error.set(this.message(error, 'Não foi possível carregar as contas a pagar.'));
    } finally {
      this.loading.set(false);
      this.paymentsLoading.set(false);
    }
  }

  async select(payable: Payable): Promise<void> {
    this.error.set('');
    this.notice.set('');
    this.selected.set(payable);
    this.payments.set([]);
    this.paymentsLoading.set(true);
    this.paymentsLoadFailed.set(false);
    try {
      this.payments.set(await firstValueFrom(this.api.payablePayments(payable.id)));
    } catch (error) {
      this.paymentsLoadFailed.set(true);
      this.error.set(this.message(error, 'Não foi possível carregar os pagamentos.'));
    } finally {
      this.paymentsLoading.set(false);
    }
  }

  startEdit(payable: Payable): void {
    this.editingId = payable.id;
    this.categoryId = payable.categoryId;
    this.entityId = payable.entityId;
    this.necessity = payable.necessity;
    this.description = payable.description;
    this.amount = payable.amount;
    this.dueOn = payable.dueOn;
    this.error.set('');
  }

  resetForm(): void {
    this.editingId = null;
    this.categoryId = null;
    this.entityId = null;
    this.necessity = null;
    this.description = '';
    this.amount = null;
    this.dueOn = '';
  }

  async save(): Promise<void> {
    if (this.busy()) return;
    if (this.categoryId === null || this.necessity === null || this.amount === null
      || this.amount <= 0 || !this.dueOn) {
      this.error.set('Informe categoria, necessidade, valor positivo e vencimento.');
      return;
    }
    const editing = this.editingId;
    const data = { categoryId: this.categoryId, entityId: this.entityId,
      necessity: this.necessity, description: this.description, amount: this.amount, dueOn: this.dueOn };
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      const saved = await firstValueFrom(editing === null
        ? this.api.createPayable(data) : this.api.updatePayable(editing, data));
      this.month = saved.dueOn.slice(0, 7);
      this.resetForm();
      await this.refreshAfterMutation(editing === null
        ? 'Conta cadastrada sem alterar o saldo.' : 'Conta atualizada.');
    } catch (error) {
      this.error.set(this.message(error, editing === null
        ? 'Não foi possível cadastrar a conta.' : 'Não foi possível atualizar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }

  async cancel(payable: Payable): Promise<void> {
    if (this.busy() || !confirm(`Cancelar a conta "${payable.description || payable.categoryName}"?`)) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.cancelPayable(payable.id));
      await this.refreshAfterMutation('Conta cancelada. Nenhum saldo foi alterado.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível cancelar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }

  async pay(): Promise<void> {
    const payable = this.selected();
    if (!payable || this.busy()) return;
    if (this.walletId === null || !this.paidOn) {
      this.error.set('Selecione a conta pagadora e informe a data efetiva do pagamento.');
      return;
    }
    if (!confirm(`Confirmar pagamento integral de ${money(payable.amount)}? O saldo da conta será reduzido.`)) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.payPayable(payable.id, this.walletId, this.paidOn));
      await this.refreshAfterMutation('Pagamento integral registrado. Saldo e pendência atualizados.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível registrar o pagamento.'));
    } finally {
      this.busy.set(false);
    }
  }

  async reverse(payment: PayablePayment): Promise<void> {
    const payable = this.selected();
    if (!payable || this.busy()) return;
    if (!this.reversalReason.trim()) {
      this.error.set('Informe o motivo da correção do pagamento.');
      return;
    }
    if (!confirm('Estornar este registro de pagamento? A conta voltará a ficar pendente e o saldo estimado será corrigido.')) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.csrf());
      await firstValueFrom(this.api.reversePayablePayment(payable.id, payment.id, this.reversalReason));
      this.reversalReason = '';
      await this.refreshAfterMutation('Pagamento estornado. O histórico foi preservado.');
    } catch (error) {
      this.error.set(this.message(error, 'Não foi possível estornar o pagamento.'));
    } finally {
      this.busy.set(false);
    }
  }

  private async refresh(): Promise<void> {
    const [items, wallets, summary] = await Promise.all([
      firstValueFrom(this.api.payables(this.month)),
      firstValueFrom(this.api.wallets()),
      firstValueFrom(this.api.monthlySummary(this.store.selectedMonth))
    ]);
    this.payables.set(items);
    this.store.wallets.set(wallets);
    this.store.monthlySummary.set(summary);
    if (this.selected()) {
      const id = this.selected()!.id;
      const [detail, history] = await Promise.all([
        firstValueFrom(this.api.payable(id)), firstValueFrom(this.api.payablePayments(id))
      ]);
      this.selected.set(detail);
      this.payments.set(history);
    }
  }

  private async refreshAfterMutation(message: string): Promise<void> {
    this.notice.set(message);
    try {
      await this.refresh();
    } catch {
      this.selected.set(null);
      this.payables.set([]);
      this.loadFailed.set(true);
      this.error.set('A operação foi concluída, mas não foi possível atualizar a tela. Use Consultar para recarregar.');
    }
  }

  private message(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 404) return 'Conta, categoria ou carteira indisponível para este usuário.';
      if (error.status === 409) return 'A conta já foi quitada, cancelada ou alterada. Atualize e tente novamente.';
      if (error.status === 400) return 'Confira os dados e a data informados.';
      if (error.status === 401) return 'Sua sessão expirou. Entre novamente.';
      if (error.status === 403) return 'Verificação de segurança falhou. Recarregue a página.';
    }
    return fallback;
  }
}
