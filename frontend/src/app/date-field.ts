import { Component, forwardRef, inject, Input } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { PrimeNG } from 'primeng/config';
import { setBrazilianLocale } from './locale';

/** Keeps API dates in ISO while showing Brazilian dates in the picker. */
@Component({
  selector: 'app-date-field',
  imports: [FormsModule, DatePickerModule],
  template: `<p-datepicker [inputId]="inputId" [ngModel]="date" (ngModelChange)="change($event)"
               (onBlur)="onTouched()" [disabled]="disabled" [view]="month ? 'month' : 'date'"
               [dateFormat]="month ? 'mm/yy' : 'dd/mm/yy'" [showIcon]="true"
               [showTime]="time" hourFormat="24" [readonlyInput]="true" [appendTo]="'body'"
               [placeholder]="month ? 'mm/aaaa' : time ? 'dd/mm/aaaa hh:mm' : 'dd/mm/aaaa'" />`,
  styles: [':host { display: block; width: 100%; } p-datepicker { display: flex; width: 100%; }'],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateField), multi: true }]
})
export class DateField implements ControlValueAccessor {
  @Input({ required: true }) inputId = '';
  @Input() month = false;
  @Input() time = false;
  date: Date | null = null;
  disabled = false;
  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  constructor() {
    setBrazilianLocale(inject(PrimeNG));
  }

  writeValue(value: string | null): void {
    if (!value) { this.date = null; return; }
    const [year, month, day] = value.slice(0, 10).split('-').map(Number);
    const [hours, minutes] = (value.slice(11, 16) || '00:00').split(':').map(Number);
    this.date = year && month ? new Date(year, month - 1, day || 1, hours || 0, minutes || 0) : null;
  }

  registerOnChange(fn: (value: string) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; }

  change(date: Date | null): void {
    this.date = date;
    this.onChange(date ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` +
      (this.month ? '' : `-${String(date.getDate()).padStart(2, '0')}` +
        (this.time ? `T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` : '')) : '');
  }
}
