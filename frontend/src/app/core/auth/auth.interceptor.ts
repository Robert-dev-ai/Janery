import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

const RUTAS_SIN_TOKEN = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

function conToken(req: HttpRequest<unknown>, token: string | null) {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** Solo envía el token a nuestra API, y renueva una vez si caducó. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const esApi = req.url.startsWith(environment.apiBaseUrl);
  if (!esApi || RUTAS_SIN_TOKEN.some((ruta) => req.url.endsWith(ruta))) {
    return next(req);
  }
  const auth = inject(AuthService);
  return next(conToken(req, auth.token())).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) {
        return throwError(() => error);
      }
      return auth.renovar().pipe(
        switchMap((token) => (token ? next(conToken(req, token)) : throwError(() => error))),
      );
    }),
  );
};
