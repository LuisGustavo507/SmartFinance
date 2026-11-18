import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, firstValueFrom, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

// A single renewal is shared by concurrent requests in this tab. The browser
// lock serializes renewals across tabs; cookies are read when the POST is sent.
let renewal: Promise<void> | undefined;

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const http = inject(HttpClient);
  if (!request.url.startsWith('/api/') || request.url.startsWith('/api/auth/') &&
      request.url !== '/api/auth/me') {
    return next(request);
  }

  return next(request).pipe(catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
      return throwError(() => error);
    }
    renewal ??= renew(http).finally(() => { renewal = undefined; });
    // A 401 from a protected route is rejected before the controller runs:
    // the mutation has not been applied and is safe to retry exactly once.
    return from(renewal).pipe(switchMap(() => next(request)));
  }));
};

async function renew(http: HttpClient): Promise<void> {
  const run = () => firstValueFrom(http.post<void>('/api/auth/refresh', {}));
  if (typeof navigator !== 'undefined' && navigator.locks) {
    await navigator.locks.request('smartfinance-refresh', run);
  } else {
    await run();
  }
}
