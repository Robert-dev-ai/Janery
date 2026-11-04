import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService, Yo } from '../../core/auth/auth';
import { Contrasena } from './contrasena';
import { PerfilPagina } from './perfil';

describe('configuración', () => {
  const api = environment.apiBaseUrl;
  const perfil = { nombres: 'Ana', apellidos: 'Paz', identificacion: '1234567890', telefono: '+593 991234567', direccion: 'Quito' };
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function sesion(tienePerfil: boolean): void {
    TestBed.inject(AuthService).yo.set({ correo: 'a@b.ec', tienePerfil } as Yo);
  }

  it('sin datos personales invita a completarlos y no los consulta', () => {
    sesion(false);
    const fixture = TestBed.createComponent(PerfilPagina);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Completar datos');
  });

  function abrirEdicion() {
    sesion(true);
    const fixture = TestBed.createComponent(PerfilPagina);
    fixture.detectChanges();
    http.expectOne(`${api}/personas/me`).flush(perfil);
    fixture.detectChanges();
    const elemento: HTMLElement = fixture.nativeElement;
    elemento.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    return { fixture, elemento };
  }

  function escribir(elemento: HTMLElement, nombre: string, valor: string): void {
    const input = elemento.querySelector<HTMLInputElement>(`input[formcontrolname=${nombre}]`)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  it('edita teléfono y dirección; nombres y cédula no son campos', () => {
    const { fixture, elemento } = abrirEdicion();
    // Correo, prefijo y número del teléfono, y la dirección.
    expect(elemento.querySelectorAll('form input').length).toBe(4);
    for (const fijo of ['nombres', 'apellidos', 'identificacion']) {
      expect(elemento.querySelector(`input[formcontrolname=${fijo}]`)).toBeNull();
    }
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    const peticion = http.expectOne(`${api}/personas/me`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ telefono: '+593 991234567', direccion: 'Quito' });
    peticion.flush({ ...perfil, telefono: '+593 981234567' });
    fixture.detectChanges();
    expect(elemento.textContent).toContain('+593 981234567');
    expect(elemento.textContent).toContain('Datos actualizados');
  });

  it('para cambiar el correo pide la contraseña actual', () => {
    const { fixture, elemento } = abrirEdicion();
    expect(elemento.querySelector('input[formcontrolname=contrasena]')).toBeNull();
    escribir(elemento, 'correo', 'Nuevo@B.ec');
    fixture.detectChanges();
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    // Sin la contraseña no se envía nada.
    expect(elemento.textContent).toContain('La contraseña actual es obligatorio');

    escribir(elemento, 'contrasena', 'Clave-Actual-1');
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const correo = http.expectOne(`${api}/auth/correo`);
    expect(correo.request.body).toEqual({ correo: 'Nuevo@B.ec', contrasena: 'Clave-Actual-1' });
    correo.flush(null);
    http.expectOne(`${api}/auth/me`).flush({ correo: 'nuevo@b.ec', tienePerfil: true });
    http.expectOne(`${api}/personas/me`).flush(perfil);
    fixture.detectChanges();
    expect(elemento.textContent).toContain('nuevo@b.ec');
  });

  describe('contraseña', () => {
    function rellenar(nueva: string, confirmacion: string) {
      const fixture = TestBed.createComponent(Contrasena);
      fixture.detectChanges();
      const elemento: HTMLElement = fixture.nativeElement;
      const valores = ['Clave-Actual-1', nueva, confirmacion];
      elemento.querySelectorAll('input').forEach((input, i) => {
        input.value = valores[i];
        input.dispatchEvent(new Event('input'));
      });
      fixture.detectChanges();
      return { fixture, elemento };
    }

    it('no deja enviar si la confirmación no coincide', () => {
      const { elemento } = rellenar('Clave-Nueva-1', 'Otra-Clave-1');
      expect(elemento.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();
      expect(elemento.textContent).toContain('no coinciden');
    });

    it('envía la cookie y vacía el formulario al terminar', () => {
      const { fixture, elemento } = rellenar('Clave-Nueva-1', 'Clave-Nueva-1');
      elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

      const peticion = http.expectOne(`${api}/auth/contrasena`);
      expect(peticion.request.withCredentials).toBeTrue();
      expect(peticion.request.body).toEqual({ actual: 'Clave-Actual-1', nueva: 'Clave-Nueva-1' });
      peticion.flush(null);
      // Al cambiarla se recarga el usuario: así se levanta la obligación de cambiar una contraseña temporal.
      http.expectOne(`${api}/auth/me`).flush({ correo: 'a@b.ec', debeCambiarContrasena: false });
      fixture.detectChanges();

      expect(elemento.textContent).toContain('Contraseña cambiada');
      expect(elemento.querySelector('input')!.value).toBe('');
    });

    it('muestra el error del servidor', () => {
      const { fixture, elemento } = rellenar('Clave-Nueva-1', 'Clave-Nueva-1');
      elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      http.expectOne(`${api}/auth/contrasena`).flush(
        { mensaje: 'La contraseña actual no es correcta' },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();
      expect(elemento.textContent).toContain('La contraseña actual no es correcta');
    });
  });
});
