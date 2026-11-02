import { FormControl } from '@angular/forms';

import { cedulaValida, fechaMayorDeEdad, mayorDeEdad, separarTelefono, telefonoEsValido } from './validadores';

describe('validadores', () => {
  it('la cédula tiene exactamente 10 números', () => {
    expect(cedulaValida(new FormControl('1004779680'))).toBeNull();
    expect(cedulaValida(new FormControl('12345678901'))).toEqual({ cedula: true });
    expect(cedulaValida(new FormControl('123456789a'))).toEqual({ cedula: true });
  });

  it('en Ecuador, un celular tiene 9 números sin el 0 y un fijo 8', () => {
    expect(telefonoEsValido('+593 991234567')).toBeTrue();
    expect(telefonoEsValido('+593 22345678')).toBeTrue();
    expect(telefonoEsValido('+593 0991234567')).toBeFalse();
    expect(telefonoEsValido('+593 99123456')).toBeFalse();
    expect(telefonoEsValido('+1 2025550123')).toBeTrue();
    expect(telefonoEsValido('0991234567')).toBeFalse();
  });

  it('separa teléfonos guardados en el formato anterior quitando el 0', () => {
    expect(separarTelefono('0991234567')).toEqual({ prefijo: '+593', numero: '991234567' });
    expect(separarTelefono('+593 991234567')).toEqual({ prefijo: '+593', numero: '991234567' });
    expect(separarTelefono('+34 612345678')).toEqual({ prefijo: '+34', numero: '612345678' });
  });

  it('exige mayoría de edad', () => {
    expect(mayorDeEdad(new FormControl(fechaMayorDeEdad()))).toBeNull();
    expect(mayorDeEdad(new FormControl(new Date().toISOString().slice(0, 10)))).toEqual({ menorDeEdad: true });
  });
});
