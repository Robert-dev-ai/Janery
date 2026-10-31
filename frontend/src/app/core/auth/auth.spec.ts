import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth';

describe('autenticación', () => {
  const api = environment.apiBaseUrl;
  let http: HttpTestingController;
  let auth: AuthService;
  let cliente: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
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
});
