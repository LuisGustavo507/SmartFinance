import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';
import { sessionInterceptor } from './session.interceptor';
import { routes } from './app.routes';

const FinanceTheme = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#E9F1FA', 100: '#D6ECF8', 200: '#B6E2F3',
      300: '#83D2EC', 400: '#41BCE7', 500: '#00ABE4',
      600: '#008FBE', 700: '#006E94', 800: '#005572',
      900: '#033B51', 950: '#052B3B'
    },
    colorScheme: {
      light: {
        primary: {
          color: '{primary.700}', inverseColor: '#FFFFFF',
          hoverColor: '{primary.800}', activeColor: '{primary.900}'
        }
      }
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([sessionInterceptor])
    ),
    provideRouter(routes),
    providePrimeNG({ theme: { preset: FinanceTheme, options: { darkModeSelector: 'none' } } })
  ]
};
