import { Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { FinanceStore } from './finance-store';

@Component({
  selector: 'app-shell',
  imports: [ButtonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.css'
})
export class Shell {
  readonly store = inject(FinanceStore);
  private readonly router = inject(Router);
  readonly menuOpen = signal(false);

  constructor() {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntilDestroyed()
    ).subscribe(() => {
      this.store.notice.set('');
      this.store.error.set('');
    });
    effect(() => {
      // A 401 during a request clears the profile; send the user back to login.
      if (this.store.ready() && !this.store.profile()) {
        void this.router.navigateByUrl('/login');
      }
    });
  }

  toggleMenu(): void {
    this.menuOpen.update(open => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  async logout(): Promise<void> {
    await this.store.logout();
    if (!this.store.profile()) {
      this.closeMenu();
      await this.router.navigateByUrl('/login');
    }
  }
}
