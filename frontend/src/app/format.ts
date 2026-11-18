import { CategoryTotal, Necessity } from './api';

export interface CategoryRow {
  name: string;
  kind: 'RECEITA' | 'DESPESA';
  realized: number;
  planned: number;
}

export function categoryRows(totals: CategoryTotal[] | undefined): CategoryRow[] {
  return (totals ?? []).map(total => ({
    name: total.categoryName,
    kind: total.kind,
    realized: total.kind === 'RECEITA' ? total.realizedIncome : total.realizedExpenses,
    planned: total.kind === 'RECEITA' ? total.plannedIncome : total.plannedExpenses
  }));
}

export function necessityLabel(necessity: Necessity | null): string {
  return necessity === 'NECESSARIA' ? 'Necessária'
    : necessity === 'NEUTRA' ? 'Neutra'
    : necessity === 'FUTIL' ? 'Fútil' : 'Não classificada';
}

export function money(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function shortDate(value: string | null): string {
  if (!value) return '—';
  const [year, month, day] = value.slice(0, 10).split('-');
  return day && month && year ? `${day}/${month}/${year}` : value;
}

export function monthLabel(value: string): string {
  const [year, month] = value.split('-').map(Number);
  if (!year || !month) return value;
  return new Date(Date.UTC(year, month - 1, 1))
    .toLocaleDateString('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' });
}
