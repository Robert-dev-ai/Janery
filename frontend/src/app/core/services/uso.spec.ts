import { rutaDe } from './uso';

describe('rutaDe', () => {
  it('quita parámetros y números de registro, y conserva el informe abierto', () => {
    expect(rutaDe('/app/creditos')).toBe('/app/creditos');
    expect(rutaDe('/admin/creditos/12?x=1#a')).toBe('/admin/creditos/:id');
    expect(rutaDe('/admin/reportes?informe=ACTIVIDAD')).toBe('/admin/reportes/actividad');
    expect(rutaDe('/')).toBe('/');
  });
});
