import { coincide, nombreModalidad, sumar } from './hojas';

describe('hojas de socios', () => {
  it('suma importes sin restos de decimales y omite los vacíos', () => {
    expect(sumar([0.1, 0.2, null, undefined])).toBe(0.3);
    expect(sumar([])).toBe(0);
  });

  it('filtra por grupo y busca sin tildes y en cualquier orden', () => {
    const socio = { nombre: 'Pérez Ana María', socio: true };
    expect(coincide(socio, 'TODOS', '')).toBe(true);
    expect(coincide(socio, 'CLIENTES', '')).toBe(false);
    expect(coincide(socio, 'SOCIOS', 'ana perez')).toBe(true);
    expect(coincide(socio, 'SOCIOS', 'luis')).toBe(false);
  });

  it('nombra las modalidades', () => {
    expect(nombreModalidad('INVERSION')).toBe('Inversión');
    expect(nombreModalidad(null)).toBe('');
  });
});
