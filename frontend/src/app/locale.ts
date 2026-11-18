import { PrimeNG } from 'primeng/config';

export function setBrazilianLocale(config: PrimeNG): void {
  config.setTranslation({
    dayNames: ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'],
    dayNamesShort: ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'],
    dayNamesMin: ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'],
    monthNames: ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
      'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'],
    monthNamesShort: ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'],
    today: 'Hoje', clear: 'Limpar', firstDayOfWeek: 0,
    chooseDate: 'Escolher data', chooseMonth: 'Escolher mês', chooseYear: 'Escolher ano',
    prevMonth: 'Mês anterior', nextMonth: 'Próximo mês',
    prevYear: 'Ano anterior', nextYear: 'Próximo ano',
    passwordPrompt: 'Digite uma senha', weak: 'Fraca', medium: 'Média', strong: 'Forte'
  });
}
