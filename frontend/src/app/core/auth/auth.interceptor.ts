import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

/** Rutas públicas de la sesión: un 401 en ellas es la respuesta esperada, no un token vencido que renovar. */
const RUTAS_SIN_TOKEN = ['/auth/login', '/auth/registro', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/recuperar'];

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
  const usado = auth.token();
  return next(conToken(req, usado)).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) {
        return throwError(() => error);
      }
      // Salió con un token que otra petición ya renovó: basta con repetirla con el actual.
      const actual = auth.token();
      if (actual && actual !== usado) {
        return next(conToken(req, actual));
      }
      return auth.renovar().pipe(
        switchMap((token) => (token ? next(conToken(req, token)) : throwError(() => error))),
      );
    }),
  );
};
