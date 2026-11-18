import { TestBed } from '@angular/core/testing';
import { MoneyField } from './money-field';
import { DateField } from './date-field';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';

describe('Brazilian form fields', () => {
  it('converts digit input to cents and preserves a negative initial balance', () => {
    TestBed.configureTestingModule({ imports: [MoneyField] });
    const fixture = TestBed.createComponent(MoneyField);
    fixture.componentInstance.inputId = 'test-money';
    fixture.componentInstance.allowNegative = true;
    const values: (number | null)[] = [];
    fixture.componentInstance.registerOnChange(value => values.push(value));
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = '4599';
    input.dispatchEvent(new Event('input'));
    expect(input.value).toMatch(/45,99/);
    expect(values.at(-1)).toBe(45.99);
    input.value = '-1250';
    input.dispatchEvent(new Event('input'));
    expect(values.at(-1)).toBe(-12.5);
    input.value = '';
    input.dispatchEvent(new Event('input'));
    expect(values.at(-1)).toBeNull();
  });

  it('maps ISO dates to Brazilian display without losing the month', async () => {
    TestBed.configureTestingModule({
      imports: [DateField],
      providers: [providePrimeNG({ theme: { preset: Aura } })]
    });
    const fixture = TestBed.createComponent(DateField);
    fixture.componentInstance.inputId = 'test-date';
    fixture.componentInstance.writeValue('2026-11-15');
    const dates: string[] = [];
    fixture.componentInstance.registerOnChange(value => dates.push(value));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('#test-date') as HTMLInputElement).value)
      .toBe('15/11/2026');
    fixture.componentInstance.change(new Date(2026, 11, 1));
    expect(dates.at(-1)).toBe('2026-12-01');
    fixture.componentInstance.month = true;
    fixture.componentInstance.change(new Date(2027, 0, 1));
    expect(dates.at(-1)).toBe('2027-01');
  });
});
