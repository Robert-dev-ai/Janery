import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { Inicio } from './inicio';

describe('Inicio', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Inicio],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function renderizar() {
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    return fixture;
  }

  it('informa que comprueba la conexión mientras carga', () => {
    const fixture = renderizar();
    expect(fixture.nativeElement.textContent).toContain('Comprobando');
    http.expectOne(`${environment.apiBaseUrl}/public/estado`).flush({ servicio: 'janery', estado: 'disponible' });
  });

  it('informa servidor disponible', () => {
    const fixture = renderizar();
    http.expectOne(`${environment.apiBaseUrl}/public/estado`).flush({ servicio: 'janery', estado: 'disponible' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Servidor disponible');
  });

  it('informa error de conexión', () => {
    const fixture = renderizar();
    http
      .expectOne(`${environment.apiBaseUrl}/public/estado`)
      .flush('', { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se pudo conectar');
  });
});
