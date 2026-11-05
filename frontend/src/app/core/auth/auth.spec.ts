import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService, AVISO_SESION_TERMINADA, Yo } from './auth';

describe('autenticación', () => {
  const api = environment.apiBaseUrl;
  let http: HttpTestingController;
  let auth: AuthService;
  let cliente: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    cliente = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('añade el token solo a peticiones de la API', () => {
    auth.token.set('abc');
    cliente.get(`${api}/personas/me`).subscribe();
    cliente.get('https://otro.test/datos').subscribe();
    expect(http.expectOne(`${api}/personas/me`).request.headers.get('Authorization')).toBe('Bearer abc');
    expect(http.expectOne('https://otro.test/datos').request.headers.has('Authorization')).toBeFalse();
  });

  it('ante un 401 renueva una sola vez y reintenta las peticiones', () => {
    auth.token.set('viejo');
    let respuestas = 0;
    cliente.get(`${api}/a`).subscribe(() => respuestas++);
    cliente.get(`${api}/b`).subscribe(() => respuestas++);
    http.expectOne(`${api}/a`).flush('', { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${api}/b`).flush('', { status: 401, statusText: 'Unauthorized' });

    http.expectOne(`${api}/auth/refresh`).flush({ accessToken: 'nuevo', expiraEn: '' });

    const reintentoA = http.expectOne(`${api}/a`);
    expect(reintentoA.request.headers.get('Authorization')).toBe('Bearer nuevo');
    reintentoA.flush({});
    http.expectOne(`${api}/b`).flush({});
    expect(respuestas).toBe(2);
  });

  it('si la renovación falla, limpia la sesión y propaga el error', () => {
    auth.token.set('viejo');
    let error = 0;
    cliente.get(`${api}/a`).subscribe({ error: () => error++ });
    http.expectOne(`${api}/a`).flush('', { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${api}/auth/refresh`).flush('', { status: 401, statusText: 'Unauthorized' });
    expect(error).toBe(1);
    expect(auth.token()).toBeNull();
  });

  const yo: Yo = { id: 1, correo: 'a@b.c', cargos: [], permisos: [], tienePerfil: true, estadoCuenta: 'ACTIVA', esSocio: false, nombre: 'Ana', tieneFoto: false, debeCambiarContrasena: false };
  const usada = { status: 401, statusText: 'Unauthorized' };

  it('si otra pestaña usó el token de refresco, reintenta una vez y conserva la sesión', fakeAsync(() => {
    let token: string | null = 'sin respuesta';
    auth.renovar().subscribe((t) => (token = t));
    http.expectOne(`${api}/auth/refresh`).flush({ codigo: 'SESION_EXPIRADA' }, usada);
    tick(300);
    http.expectOne(`${api}/auth/refresh`).flush({ accessToken: 'de-la-otra-pestana', expiraEn: '' });
    expect(token as string | null).toBe('de-la-otra-pestana');
    expect(auth.token()).toBe('de-la-otra-pestana');
  }));

  it('sin cookie de sesión no reintenta: abrir la página sin sesión no espera', () => {
    let token: string | null = 'sin respuesta';
    auth.renovar().subscribe((t) => (token = t));
    http.expectOne(`${api}/auth/refresh`).flush({ codigo: 'SIN_SESION' }, usada);
    expect(token as string | null).toBeNull();
  });

  it('al perder una sesión abierta lleva a iniciar sesión con un aviso', fakeAsync(() => {
    const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    auth.yo.set(yo);
    auth.renovar().subscribe();
    http.expectOne(`${api}/auth/refresh`).flush({ codigo: 'SESION_EXPIRADA' }, usada);
    tick(300);
    http.expectOne(`${api}/auth/refresh`).flush({ codigo: 'SESION_EXPIRADA' }, usada);
    expect(auth.autenticado()).toBeFalse();
    expect(navegar).toHaveBeenCalledWith('/iniciar-sesion', { state: { aviso: AVISO_SESION_TERMINADA } });
  }));

  it('sin sesión previa, que falle la renovación no navega', () => {
    const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl');
    auth.renovar().subscribe();
    http.expectOne(`${api}/auth/refresh`).flush({ codigo: 'SIN_SESION' }, usada);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un 401 de una petición que salió con un token ya renovado se repite con el actual, sin renovar otra vez', () => {
    auth.token.set('viejo');
    let respuesta = false;
    cliente.get(`${api}/lenta`).subscribe(() => (respuesta = true));
    auth.token.set('nuevo');
    http.expectOne(`${api}/lenta`).flush('', usada);
    const repetida = http.expectOne(`${api}/lenta`);
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer nuevo');
    repetida.flush({});
    http.expectNone(`${api}/auth/refresh`);
    expect(respuesta).toBeTrue();
  });

  it('las rutas públicas de la sesión no se renuevan ante un 401', () => {
    auth.token.set('abc');
    cliente.post(`${api}/auth/registro`, {}).subscribe({ error: () => undefined });
    cliente.post(`${api}/auth/recuperar`, {}).subscribe({ error: () => undefined });
    const registro = http.expectOne(`${api}/auth/registro`);
    expect(registro.request.headers.has('Authorization')).toBeFalse();
    registro.flush({});
    http.expectOne(`${api}/auth/recuperar`).flush('', usada);
    http.expectNone(`${api}/auth/refresh`);
  });

  it('al cambiar una contraseña temporal renueva el token, que llevaba la marca', () => {
    auth.yo.set({ ...yo, debeCambiarContrasena: true });
    auth.token.set('con-marca');
    auth.cambiarContrasena('Temporal-1', 'Nueva-Clave-1').subscribe();
    http.expectOne(`${api}/auth/contrasena`).flush(null);
    http.expectOne(`${api}/auth/refresh`).flush({ accessToken: 'sin-marca', expiraEn: '' });
    http.expectOne(`${api}/auth/me`).flush({ ...yo, debeCambiarContrasena: false });
    expect(auth.token()).toBe('sin-marca');
    expect(auth.yo()?.debeCambiarContrasena).toBeFalse();
  });
});
