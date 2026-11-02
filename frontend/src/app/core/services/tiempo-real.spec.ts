import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService, Yo } from '../auth/auth';
import { TiempoRealService } from './tiempo-real';

describe('TiempoRealService', () => {
  const yo: Yo = { id: 1, correo: 'a@b.c', cargos: [], permisos: [], tienePerfil: true, estadoCuenta: 'ACTIVA', esSocio: false, nombre: 'Ana', tieneFoto: false, debeCambiarContrasena: false };
  let enviar: (texto: string) => void;
  let llamadas: RequestInit[];
  let auth: AuthService;
  let servicio: TiempoRealService;

  beforeEach(() => {
    llamadas = [];
    const cuerpo = new ReadableStream<Uint8Array>({
      start: (control) => {
        enviar = (texto) => control.enqueue(new TextEncoder().encode(texto));
      },
    });
    spyOn(window, 'fetch').and.callFake(async (_url, opciones) => {
      llamadas.push(opciones!);
      return new Response(cuerpo, { status: 200 });
    });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    servicio = TestBed.inject(TiempoRealService);
  });

  const esperar = (ms: number) => new Promise((listo) => setTimeout(listo, ms));

  it('sin sesión no se conecta', () => {
    TestBed.tick();
    expect(window.fetch).not.toHaveBeenCalled();
    expect(servicio.conectado()).toBeFalse();
  });

  it('se conecta con el token y agrupa los avisos en una sola actualización', async () => {
    auth.token.set('abc');
    auth.yo.set(yo);
    TestBed.tick();
    await esperar(10);
    expect((llamadas[0].headers as Record<string, string>)['Authorization']).toBe('Bearer abc');
    expect(servicio.conectado()).toBeTrue();

    let avisos = 0;
    servicio.cambios.subscribe(() => avisos++);
    enviar(':latido\n\n');
    enviar('event:cambio\ndata:CUENTA\n\n');
    enviar('event:cambio\ndata:CREDITO\n\n');
    await esperar(350);

    expect(avisos).toBe(1);
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/auth/me`).flush(yo);

    auth.yo.set(null);
    TestBed.tick();
    expect(servicio.conectado()).toBeFalse();
  });
});
