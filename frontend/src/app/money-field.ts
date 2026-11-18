import { Component, forwardRef, Input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';

/** Digit entry in cents: typing 4599 produces R$ 45,99. */
@Component({
  selector: 'app-money-field',
  imports: [InputTextModule],
  template: `<input pInputText [id]="inputId" type="text" inputmode="numeric" autocomplete="off"
                    placeholder="R$ 0,00" [attr.aria-label]="ariaLabel || null"
                    [value]="display" [disabled]="disabled"
                    (focus)="selectAll($event)" (input)="type($event)"
                    (blur)="onTouched()" />`,
  styles: [':host { display: block; width: 100%; } :host input { width: 100%; }'],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MoneyField), multi: true }]
})
export class MoneyField implements ControlValueAccessor {
  @Input({ required: true }) inputId = '';
  @Input() ariaLabel = '';
  @Input() allowNegative = false;
  display = '';
  disabled = false;
  private onChange: (value: number | null) => void = () => {};
  onTouched: () => void = () => {};

  writeValue(value: number | null): void {
    this.display = value == null ? '' : this.format(value);
  }
  registerOnChange(fn: (value: number | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(value: boolean): void { this.disabled = value; }

  selectAll(event: FocusEvent): void {
    (event.target as HTMLInputElement).select();
  }

  type(event: Event): void {
    const input = event.target as HTMLInputElement;
    const raw = input.value;
    const digits = raw.replace(/\D/g, '').slice(0, 19);
    const negative = this.allowNegative && raw.includes('-');
    const value = digits ? Number(digits) / 100 * (negative ? -1 : 1) : null;
    this.display = value == null ? '' : this.format(value);
    input.value = this.display;
    input.setSelectionRange(input.value.length, input.value.length);
    this.onChange(value);
  }

  private format(value: number): string {
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
