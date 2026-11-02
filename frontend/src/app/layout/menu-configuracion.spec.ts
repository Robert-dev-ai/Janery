import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth';
import { MenuConfiguracion } from './menu-configuracion';

describe('MenuConfiguracion', () => {
  function renderizar() {
    TestBed.configureTestingModule({
      imports: [MenuConfiguracion],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(MenuConfiguracion);
    fixture.detectChanges();
    return fixture;
  }

  function abrir(fixture: ReturnType<typeof renderizar>): HTMLElement {
    const elemento: HTMLElement = fixture.nativeElement;
    elemento.querySelector<HTMLButtonElement>('.ajustes-boton')!.click();
    fixture.detectChanges();
    return elemento;
  }

  it('muestra la ruedita y abre las opciones al pulsarla', () => {
    const fixture = renderizar();
    expect(fixture.nativeElement.querySelector('.ajustes-boton svg')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[role=menu]')).toBeNull();

    const elemento = abrir(fixture);

    const texto = elemento.querySelector('[role=menu]')!.textContent;
    for (const opcion of ['Perfil', 'Cambiar contraseña', 'Sesiones', 'Cerrar sesión']) {
      expect(texto).toContain(opcion);
    }
    expect(elemento.querySelector('.ajustes-boton')!.getAttribute('aria-expanded')).toBe('true');
  });

  it('se cierra con Escape y al pulsar fuera', () => {
    const fixture = renderizar();
    const elemento = abrir(fixture);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(elemento.querySelector('[role=menu]')).toBeNull();

    abrir(fixture);
    document.body.click();
    fixture.detectChanges();
    expect(elemento.querySelector('[role=menu]')).toBeNull();
  });

  it('cierra la sesión', () => {
    const fixture = renderizar();
    TestBed.inject(AuthService).token.set('abc');
    const elemento = abrir(fixture);

    [...elemento.querySelectorAll<HTMLButtonElement>('[role=menu] button')].find((b) => b.textContent?.includes('Cerrar sesión'))!.click();

    TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    expect(TestBed.inject(AuthService).token()).toBeNull();
  });
});
