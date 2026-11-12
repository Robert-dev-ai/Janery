import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Creditos } from './creditos';

/** Antes un doble clic creaba dos solicitudes de crédito: aprobadas las dos, la persona recibía el préstamo dos veces. */
describe('Créditos: solicitar', () => {
  const ruta = `${environment.apiBaseUrl}/creditos/letras/solicitudes`;
  let http: HttpTestingController;
  let pantalla: Creditos;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    pantalla = TestBed.runInInjectionContext(() => new Creditos());
    Object.assign(pantalla, { modalidad: 'NORMAL', importe: 300, plazo: 3, origenEncaje: 'CREDITO' });
  });

  const solicitar = () => (pantalla as unknown as { solicitar(): void }).solicitar();
  const enviando = () => (pantalla as unknown as { enviando(): boolean }).enviando();

  it('un doble clic envía una sola solicitud, con clave de idempotencia', () => {
    solicitar();
    solicitar();
    const peticiones = http.match(ruta);
    expect(peticiones.length).toBe(1);
    expect(peticiones[0].request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(enviando()).toBeTrue();

    peticiones[0].flush({ id: 7 });
    expect(enviando()).toBeFalse();
  });

  it('si falla, reintentar usa la misma clave; tras un éxito, la siguiente solicitud lleva otra', () => {
    solicitar();
    const primera = http.expectOne(ruta);
    primera.flush({ codigo: 'X', mensaje: 'Sin conexión' }, { status: 503, statusText: 'No disponible' });
    expect(enviando()).toBeFalse();

    solicitar();
    const reintento = http.expectOne(ruta);
    expect(reintento.request.headers.get('Idempotency-Key')).toBe(primera.request.headers.get('Idempotency-Key'));
    reintento.flush({ id: 7 });

    Object.assign(pantalla, { importe: 300, plazo: 3, origenEncaje: 'CREDITO' });
    solicitar();
    expect(http.expectOne(ruta).request.headers.get('Idempotency-Key')).not.toBe(primera.request.headers.get('Idempotency-Key'));
  });
});
