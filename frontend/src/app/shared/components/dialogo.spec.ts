import { TestBed } from '@angular/core/testing';

import { Dialogo, DialogoService } from './dialogo';

describe('Dialogo', () => {
  function abrir() {
    const fixture = TestBed.createComponent(Dialogo);
    fixture.detectChanges();
    return { fixture, servicio: TestBed.inject(DialogoService), elemento: fixture.nativeElement as HTMLElement };
  }

  it('confirma con el botón de acción', async () => {
    const { fixture, servicio, elemento } = abrir();
    const respuesta = servicio.confirmar('¿Seguro?', 'Aprobar');
    fixture.detectChanges();
    expect(elemento.querySelector('dialog')!.open).toBeTrue();
    expect(elemento.textContent).toContain('¿Seguro?');

    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(await respuesta).toBeTrue();
    expect(elemento.querySelector('dialog')!.open).toBeFalse();
  });

  it('cancelar responde que no', async () => {
    const { fixture, servicio, elemento } = abrir();
    const respuesta = servicio.confirmar('¿Seguro?');
    fixture.detectChanges();
    elemento.querySelector<HTMLButtonElement>('button.secundario')!.click();
    expect(await respuesta).toBeFalse();
  });

  it('exige el motivo y lo devuelve sin espacios', async () => {
    const { fixture, servicio, elemento } = abrir();
    const respuesta = servicio.pedirTexto('Motivo:', 'Rechazar');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(elemento.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();

    const input = elemento.querySelector('input')!;
    input.value = '  datos incompletos ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(await respuesta).toBe('datos incompletos');
  });
});
