import { rolDe } from './etiquetas';

describe('rolDe', () => {
  it('muestra el cargo, luego socio y por último cliente', () => {
    expect(rolDe({ cargos: ['ADMINISTRACION'], esSocio: true })).toBe('Administrador');
    expect(rolDe({ cargos: ['TESORERIA', 'SECRETARIA'], esSocio: false })).toBe('Tesorería, Secretaría');
    expect(rolDe({ cargos: [], esSocio: true })).toBe('Socio');
    expect(rolDe({ cargos: [], esSocio: false })).toBe('Cliente');
  });
});
