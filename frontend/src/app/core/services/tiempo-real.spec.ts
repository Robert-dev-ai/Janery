import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService, Yo } from '../auth/auth';
import { alCambiar, TiempoRealService } from './tiempo-real';

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

  async function conectar(): Promise<void> {
    auth.token.set('abc');
    auth.yo.set(yo);
    TestBed.tick();
    await esperar(10);
  }

  it('reconoce un aviso aunque la red lo entregue partido en varios trozos', async () => {
    await conectar();
    const recibidos: ReadonlySet<string>[] = [];
    servicio.cambios.subscribe((m) => recibidos.push(m));
    enviar('event:cam');
    enviar('bio\r\nda');
    enviar('ta:CREDITO\r\n');
    enviar('\r\n');
    await esperar(350);

    expect(recibidos.length).toBe(1);
    expect([...recibidos[0]]).toEqual(['CREDITO']);
    // CREDITO no cambia los datos de la persona: no se vuelve a pedir /auth/me.
    TestBed.inject(HttpTestingController).expectNone(`${environment.apiBaseUrl}/auth/me`);
    auth.yo.set(null);
    TestBed.tick();
  });

  it('alCambiar con módulos solo reacciona a esos módulos', async () => {
    await conectar();
    let anuncios = 0;
    let todas = 0;
    TestBed.runInInjectionContext(() => {
      alCambiar(() => anuncios++, ['ANUNCIO']);
      alCambiar(() => todas++);
    });
    enviar('event:cambio\ndata:CREDITO\n\n');
    await esperar(350);
    expect(anuncios).toBe(0);
    expect(todas).toBe(1);

    enviar('event:cambio\ndata:ANUNCIO\n\n');
    await esperar(350);
    expect(anuncios).toBe(1);
    expect(todas).toBe(2);
    auth.yo.set(null);
    TestBed.tick();
  });
});
