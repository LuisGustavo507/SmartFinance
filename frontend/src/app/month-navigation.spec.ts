import { TestBed } from '@angular/core/testing';
import { MonthNavigation } from './month-navigation';

describe('MonthNavigation', () => {
  it('moves one month across year boundaries and keeps direct selection', async () => {
    await TestBed.configureTestingModule({ imports: [MonthNavigation] }).compileComponents();
    const fixture = TestBed.createComponent(MonthNavigation);
    fixture.componentInstance.inputId = 'test-month';
    fixture.componentInstance.month = '2026-12';
    const changes: string[] = [];
    fixture.componentInstance.monthChange.subscribe(month => changes.push(month));
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('dezembro de 2026');
    expect(page.querySelector('#test-month')).not.toBeNull();
    (page.querySelector('button[aria-label^="Mês seguinte"]') as HTMLButtonElement).click();
    expect(changes).toEqual(['2027-01']);

    fixture.componentInstance.month = '2027-01';
    fixture.detectChanges();
    (page.querySelector('button[aria-label^="Mês anterior"]') as HTMLButtonElement).click();
    fixture.componentInstance.change('2027-03');
    fixture.componentInstance.change('');
    fixture.componentInstance.change('2027-13');
    expect(changes).toEqual(['2027-01', '2026-12', '2027-03']);
  });
});
