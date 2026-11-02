import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AdminReportes } from './reportes';

describe('AdminReportes', () => {
  const base = `${environment.apiBaseUrl}/admin/reportes`;
  const consulta = new BehaviorSubject(convertToParamMap({}));
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminReportes],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: consulta } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir() {
    const fixture = TestBed.createComponent(AdminReportes);
    fixture.detectChanges();
    http.expectOne(base).flush([{ codigo: 'CREDITOS', titulo: 'Créditos', descripcion: 'd', usaFechas: false }]);
    fixture.detectChanges();
    return fixture;
  }

  it('muestra la tabla del informe y alinea los números', () => {
    const fixture = abrir();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    http.expectOne(`${base}/CREDITOS`).flush({
      columnas: ['Titular', 'Importe'], numericas: [false, true], filas: [['Ana', '10.00']], truncado: false,
    });
    fixture.detectChanges();
    const celdas = fixture.nativeElement.querySelectorAll('tbody td');
    expect(celdas[0].textContent).toContain('Ana');
    expect(celdas[1].classList.contains('num')).toBeTrue();
  });

  it('el menú abre directamente el informe indicado', () => {
    consulta.next(convertToParamMap({ informe: 'CREDITOS' }));
    const fixture = TestBed.createComponent(AdminReportes);
    fixture.detectChanges();
    http.expectOne(base).flush([{ codigo: 'CREDITOS', titulo: 'Créditos', descripcion: 'd', usaFechas: false }]);
    http.expectOne(`${base}/CREDITOS`).flush({ columnas: ['Titular'], numericas: [false], filas: [['Ana']], truncado: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tbody td').textContent).toContain('Ana');
    consulta.next(convertToParamMap({}));
  });

  it('avisa cuando no hay datos', () => {
    const fixture = abrir();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    http.expectOne(`${base}/CREDITOS`).flush({ columnas: ['Titular'], numericas: [false], filas: [], truncado: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin datos');
  });

  it('muestra el error seguro del backend', () => {
    const fixture = abrir();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    http.expectOne(`${base}/CREDITOS`).flush({ mensaje: 'Fechas no válidas' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Fechas no válidas');
  });
});
