import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService, Yo } from './auth';
import { autenticadoGuard, cuentaActivaGuard, permisoGuard, personalGuard, socioGuard } from './guards';

describe('guards', () => {
  const yoBase: Yo = { id: 1, correo: 'a@b.c', cargos: [], permisos: [], tienePerfil: true, estadoCuenta: 'ACTIVA', esSocio: false, nombre: 'Ana', tieneFoto: false, debeCambiarContrasena: false };

  function ejecutar(guard: typeof autenticadoGuard) {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  function rutaDe(resultado: unknown): string {
    return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] });
  });

  it('sin sesión redirige a iniciar sesión', () => {
    expect(rutaDe(ejecutar(autenticadoGuard))).toBe('/iniciar-sesion');
    expect(rutaDe(ejecutar(permisoGuard('X')))).toBe('/iniciar-sesion');
  });

  it('sin cuenta activa redirige a activar la cuenta', () => {
    TestBed.inject(AuthService).yo.set({ ...yoBase, estadoCuenta: 'PENDIENTE_PAGO' });
    expect(rutaDe(ejecutar(cuentaActivaGuard))).toBe('/activar-cuenta');
  });

  it('con cuenta activa permite el panel', () => {
    TestBed.inject(AuthService).yo.set(yoBase);
    expect(ejecutar(cuentaActivaGuard)).toBeTrue();
  });

  it('exige el permiso indicado', () => {
    TestBed.inject(AuthService).yo.set({ ...yoBase, permisos: ['ADMIN_PARAMETROS'] });
    expect(ejecutar(permisoGuard('ADMIN_PARAMETROS'))).toBeTrue();
    expect(rutaDe(ejecutar(permisoGuard('CONSULTAR_AUDITORIA')))).toBe('/admin/panel');
    TestBed.inject(AuthService).yo.set(yoBase);
    expect(rutaDe(ejecutar(permisoGuard('CONSULTAR_AUDITORIA')))).toBe('/app/inicio');
  });

  it('el área general es solo para socios, aunque se tenga un cargo con permisos', () => {
    TestBed.inject(AuthService).yo.set({ ...yoBase, permisos: ['CONSULTAR_CAJA'] });
    expect(rutaDe(ejecutar(socioGuard))).toBe('/admin/panel');
    TestBed.inject(AuthService).yo.set({ ...yoBase, esSocio: true });
    expect(ejecutar(socioGuard)).toBeTrue();
  });

  it('el panel de gestión exige algún permiso', () => {
    TestBed.inject(AuthService).yo.set(yoBase);
    expect(rutaDe(ejecutar(personalGuard))).toBe('/app/inicio');
    TestBed.inject(AuthService).yo.set({ ...yoBase, permisos: ['CONSULTAR_CAJA'] });
    expect(ejecutar(personalGuard)).toBeTrue();
  });
});
