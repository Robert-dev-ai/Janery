import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth';

// Los guards orientan la navegación; la autorización real está en el backend.

export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() || inject(Router).parseUrl('/iniciar-sesion');
};

export const cuentaActivaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.autenticado()) {
    return inject(Router).parseUrl('/iniciar-sesion');
  }
  return auth.yo()?.estadoCuenta === 'ACTIVA' || inject(Router).parseUrl('/activar-cuenta');
};

/** La autorización real está en el backend: /socios/general responde 403 a quien no es socio. */
export const socioGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.autenticado()) {
    return inject(Router).parseUrl('/iniciar-sesion');
  }
  return !!auth.yo()?.esSocio || inject(Router).parseUrl(auth.rutaInicial());
};

export const personalGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.autenticado()) {
    return inject(Router).parseUrl('/iniciar-sesion');
  }
  return !!auth.yo()?.permisos.length || inject(Router).parseUrl(auth.rutaInicial());
};

export const permisoGuard =(permiso: string): CanActivateFn => () => {
  const auth = inject(AuthService);
  if (!auth.autenticado()) {
    return inject(Router).parseUrl('/iniciar-sesion');
  }
  return auth.tienePermiso(permiso) || inject(Router).parseUrl(auth.rutaInicial());
};
