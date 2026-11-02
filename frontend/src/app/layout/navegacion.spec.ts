import { Yo } from '../core/auth/auth';
import { menuAdmin, menuPortal } from './navegacion';

describe('navegación', () => {
  const yo = (cambios: Partial<Yo>): Yo => ({
    id: 1, correo: 'a@b.ec', cargos: [], permisos: [], tienePerfil: true, estadoCuenta: 'ACTIVA', esSocio: false, ...cambios,
  });
  const titulos = (grupos: ReturnType<typeof menuAdmin>) => grupos.flatMap((g) => g.enlaces.map((e) => e.titulo));

  it('el panel solo muestra lo que permite cada permiso y oculta los grupos vacíos', () => {
    const grupos = menuAdmin(yo({ cargos: ['TESORERIA'], permisos: ['CONSULTAR_CAJA'] }));
    expect(titulos(grupos)).toEqual(['Panel', 'Tesorería', 'Estado de caja', 'Mis áreas y cargos']);
    expect(grupos.map((g) => g.titulo)).toEqual(['General', 'Finanzas y contabilidad', 'Administración']);
  });

  it('un cargo sin permisos ve sus áreas, pero no el panel', () => {
    expect(titulos(menuAdmin(yo({ cargos: ['SECRETARIA'] })))).toEqual(['Mis áreas y cargos']);
  });

  it('las entradas de informe exigen el permiso de informes', () => {
    expect(titulos(menuAdmin(yo({ permisos: ['GESTIONAR_CUENTAS'] })))).not.toContain('Encajes');
    expect(titulos(menuAdmin(yo({ permisos: ['CONSULTAR_INFORMES'] })))).toContain('Encajes');
  });

  it('el área general aparece solo a socios, aunque otro tenga todos los permisos', () => {
    expect(titulos(menuPortal(yo({ permisos: ['ADMIN_USUARIOS', 'CONSULTAR_CONTABILIDAD'] })))).not.toContain('Estado general');
    expect(titulos(menuPortal(yo({ esSocio: true })))).toContain('Estado general');
  });

  it('el fondo personal es solo para socios', () => {
    expect(titulos(menuPortal(yo({})))).not.toContain('Fondo y aportes');
    expect(titulos(menuPortal(yo({ esSocio: true })))).toContain('Fondo y aportes');
  });

  it('sin cuenta activa el portal solo ofrece activarla y los datos personales', () => {
    expect(titulos(menuPortal(yo({ estadoCuenta: null })))).toEqual(['Activar cuenta']);
  });

  it('mis cargos solo aparece a quien tiene cargo', () => {
    expect(titulos(menuPortal(yo({})))).not.toContain('Mis cargos');
    expect(titulos(menuPortal(yo({ cargos: ['TESORERIA'] })))).toContain('Mis cargos');
  });

  it('sin sesión no hay menú', () => {
    expect(menuAdmin(null)).toEqual([]);
    expect(menuPortal(null)).toEqual([]);
  });
});
