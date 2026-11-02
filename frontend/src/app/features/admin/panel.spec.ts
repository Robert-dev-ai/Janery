import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService, Yo } from '../../core/auth/auth';
import { AdminPanel } from './panel';

describe('AdminPanel', () => {
  const api = environment.apiBaseUrl;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(permisos: string[]) {
    TestBed.inject(AuthService).yo.set({ permisos, cargos: [] } as unknown as Yo);
    const fixture = TestBed.createComponent(AdminPanel);
    fixture.detectChanges();
    return fixture;
  }

  it('solo pide y muestra lo que autoriza el permiso', () => {
    const fixture = abrir(['CONSULTAR_CAJA']);
    http.expectOne(`${api}/tesoreria`).flush({ saldo: 1250.5, moneda: 'USD', movimientos: {} });
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('$1,250.50');
    expect(texto).not.toContain('Créditos por estado');
    expect(texto).not.toContain('Actividad reciente');
  });

  it('dibuja los créditos por estado con los totales del backend', () => {
    const fixture = abrir(['APROBAR_CREDITOS']);
    // FINALIZADO no viene: el backend omite los estados sin créditos.
    http.expectOne(`${api}/admin/panel/conteos`).flush({
      creditos: { SOLICITADO: 2, ACTIVO: 4, RECHAZADO: 1 }, cuentas: {}, retiros: 0, depositos: 0,
    });
    fixture.detectChanges();

    const filas = fixture.nativeElement.querySelectorAll('.grafico-fila');
    expect(filas.length).toBe(4);
    expect(filas[1].querySelector('.grafico-barra').getAttribute('width')).toBe('100%');
    expect(filas[2].querySelector('.grafico-barra')).toBeNull();
  });

  it('muestra el error del servidor', () => {
    const fixture = abrir(['CONSULTAR_CAJA']);
    http.expectOne(`${api}/tesoreria`).flush({ mensaje: 'Sin acceso' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Sin acceso');
  });
});
