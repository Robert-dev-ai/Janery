import {
  ArrowLeftRight, Banknote, BanknoteArrowDown, BookOpen, Briefcase, CreditCard, CalendarDays, ChartColumn, ChartPie, FlaskConical, ClipboardList, Coins, Contact, FilePenLine, FileText, HandCoins, History,
  Landmark, LayoutDashboard, Library, ListChecks, Lock, LucideIconData, PiggyBank, Receipt, Scale, ShieldCheck, SlidersHorizontal,
  TrendingUp, UserCog, UserPlus, Users, Vault, Wallet,
} from 'lucide-angular';

import { Yo } from '../core/auth/auth';

export interface EnlaceMenu {
  titulo: string;
  ruta: string;
  icono: LucideIconData;
  exacto?: boolean;
  /** Se muestra pequeño, debajo del enlace anterior, porque es una acción suya. */
  sub?: boolean;
  /** Abre directamente este informe en la pantalla de informes. */
  informe?: string;
  visible: (yo: Yo) => boolean;
}

export interface GrupoMenu {
  titulo: string;
  enlaces: EnlaceMenu[];
}

const conPermiso = (permiso: string) => (yo: Yo) => yo.permisos.includes(permiso);
const INFORMES = '/admin/reportes';
const informe = (titulo: string, codigo: string, icono: LucideIconData): EnlaceMenu =>
  ({ titulo, ruta: INFORMES, icono, informe: codigo, visible: conPermiso('CONSULTAR_INFORMES') });
const cuentaActiva = (yo: Yo) => yo.estadoCuenta === 'ACTIVA';
const esSocio = (yo: Yo) => cuentaActiva(yo) && yo.esSocio;

/** Solo módulos que ya existen: lo que aún no tiene pantalla no aparece. */
const ADMIN: GrupoMenu[] = [
  {
    titulo: 'General',
    enlaces: [{ titulo: 'Panel', ruta: '/admin/panel', icono: LayoutDashboard, visible: (yo) => yo.permisos.length > 0 }],
  },
  {
    titulo: 'Gestión de personas',
    enlaces: [
      { titulo: 'Personas', ruta: '/admin/personas', icono: Contact, visible: conPermiso('ADMIN_USUARIOS') },
      { titulo: 'Usuarios', ruta: '/admin/usuarios', icono: Users, visible: conPermiso('ADMIN_USUARIOS') },
      { titulo: 'Roles y permisos', ruta: '/admin/roles-permisos', icono: ShieldCheck, visible: conPermiso('ADMIN_USUARIOS') },
      { titulo: 'Socios y retiros', ruta: '/admin/retiros', icono: UserCog, visible: conPermiso('GESTIONAR_RESULTADOS') },
    ],
  },
  {
    titulo: 'Productos financieros',
    enlaces: [
      { titulo: 'Cuentas', ruta: '/admin/cuentas', icono: Wallet, visible: conPermiso('GESTIONAR_CUENTAS') },
      { titulo: 'Depósitos y pagos', ruta: '/admin/solicitudes-deposito', icono: BanknoteArrowDown, visible: conPermiso('GESTIONAR_CUENTAS') },
      { titulo: 'Retiros de dinero', ruta: '/admin/solicitudes-retiro', icono: Banknote, visible: conPermiso('GESTIONAR_CUENTAS') },
      informe('Ahorros', 'AHORROS', PiggyBank),
      { titulo: 'Créditos', ruta: '/admin/creditos', icono: HandCoins, visible: conPermiso('APROBAR_CREDITOS') },
      informe('Cuotas', 'CUOTAS', CalendarDays),
      informe('Encajes', 'ENCAJES_LETRAS', Lock),
    ],
  },
  {
    titulo: 'Finanzas y contabilidad',
    enlaces: [
      { titulo: 'Balance general', ruta: '/admin/contabilidad/balance', icono: Scale, visible: conPermiso('CONSULTAR_CONTABILIDAD') },
      { titulo: 'Libro diario', ruta: '/admin/contabilidad/diario', icono: BookOpen, visible: conPermiso('CONSULTAR_CONTABILIDAD') },
      { titulo: 'Libro mayor', ruta: '/admin/contabilidad/mayor', icono: Library, visible: conPermiso('CONSULTAR_CONTABILIDAD') },
      { titulo: 'Registro manual', ruta: '/admin/contabilidad/registro', icono: FilePenLine, visible: conPermiso('REGISTRAR_ASIENTOS') },
      { titulo: 'Tesorería', ruta: '/areas/tesoreria', icono: Landmark, visible: conPermiso('CONSULTAR_CAJA') },
      { titulo: 'Estado de caja', ruta: '/areas/estado-caja', icono: ChartPie, visible: conPermiso('CONSULTAR_CAJA') },
      { titulo: 'Caja chica', ruta: '/areas/caja-chica', icono: Receipt, visible: conPermiso('GESTIONAR_CAJA_CHICA') },
      informe('Aportes de socios', 'APORTES_SOCIOS', Coins),
      informe('Fondo de reserva', 'FONDO_RESERVA', Vault),
      { titulo: 'Resultados', ruta: '/admin/resultados', icono: TrendingUp, visible: conPermiso('GESTIONAR_RESULTADOS') },
    ],
  },
  {
    titulo: 'Análisis e informes',
    enlaces: [{ titulo: 'Informes y exportaciones', ruta: INFORMES, icono: FileText, exacto: true, visible: conPermiso('CONSULTAR_INFORMES') }],
  },
  {
    titulo: 'Historial y control',
    enlaces: [
      informe('Historial de operaciones', 'ACTIVIDAD', ListChecks),
      { titulo: 'Historial crediticio', ruta: '/admin/historial-crediticio', icono: ClipboardList, visible: conPermiso('APROBAR_CREDITOS') },
      { titulo: 'Auditoría', ruta: '/admin/auditoria', icono: History, visible: conPermiso('CONSULTAR_AUDITORIA') },
    ],
  },
  {
    titulo: 'Administración',
    enlaces: [
      { titulo: 'Uso de la aplicación', ruta: '/admin/uso', icono: ChartColumn, visible: conPermiso('VER_USO_APP') },
      { titulo: 'Simulador', ruta: '/admin/simulador', icono: FlaskConical, visible: conPermiso('USAR_SIMULADOR') },
      { titulo: 'Parámetros', ruta: '/admin/parametros', icono: SlidersHorizontal, visible: conPermiso('ADMIN_PARAMETROS') },
      { titulo: 'Mis áreas y cargos', ruta: '/areas', icono: Briefcase, exacto: true, visible: (yo) => yo.cargos.length > 0 },
    ],
  },
];

const PORTAL: GrupoMenu[] = [
  {
    titulo: 'Inicio',
    enlaces: [
      { titulo: 'Resumen', ruta: '/app/inicio', icono: LayoutDashboard, visible: cuentaActiva },
      { titulo: 'Activar cuenta', ruta: '/activar-cuenta', icono: Wallet, visible: (yo) => !cuentaActiva(yo) },
    ],
  },
  {
    titulo: 'Mis productos',
    enlaces: [
      { titulo: 'Ahorros', ruta: '/app/ahorros', icono: PiggyBank, visible: cuentaActiva },
      { titulo: 'Créditos y cuotas', ruta: '/app/creditos', icono: HandCoins, visible: cuentaActiva },
      { titulo: 'Fondo y aportes', ruta: '/app/inversiones', icono: TrendingUp, visible: esSocio },
      { titulo: 'Convertirse en socio', ruta: '/app/hacerme-socio', icono: UserPlus, visible: (yo) => cuentaActiva(yo) && !yo.esSocio },
    ],
  },
  {
    titulo: 'Historial',
    enlaces: [{ titulo: 'Movimientos', ruta: '/app/movimientos', icono: ArrowLeftRight, visible: cuentaActiva }],
  },
  {
    titulo: 'Área de socios',
    enlaces: [
      { titulo: 'Estado general', ruta: '/app/socios/general', icono: Users, visible: esSocio },
      { titulo: 'Balance general', ruta: '/app/socios/balance', icono: Scale, visible: esSocio },
      { titulo: 'Libro diario', ruta: '/app/socios/diario', icono: BookOpen, visible: esSocio },
      { titulo: 'Tesorería', ruta: '/app/socios/tesoreria', icono: Landmark, visible: esSocio },
      { titulo: 'Estado de caja', ruta: '/app/socios/estado-caja', icono: ChartPie, visible: esSocio },
      { titulo: 'Caja chica', ruta: '/app/socios/caja-chica', icono: Receipt, visible: esSocio },
      { titulo: 'Resultados', ruta: '/app/socios/resultados', icono: TrendingUp, visible: esSocio },
    ],
  },
  {
    titulo: 'Mis cargos',
    enlaces: [{ titulo: 'Mis cargos', ruta: '/mis-cargos', icono: Briefcase, visible: (yo) => yo.cargos.length > 0 }],
  },
  {
    titulo: 'Pagos',
    enlaces: [{ titulo: 'Pagar', ruta: '/app/pagar', icono: CreditCard, visible: cuentaActiva }],
  },
];

function filtrar(grupos: GrupoMenu[], yo: Yo | null): GrupoMenu[] {
  if (!yo) {
    return [];
  }
  return grupos
    .map((g) => ({ ...g, enlaces: g.enlaces.filter((e) => e.visible(yo)) }))
    .filter((g) => g.enlaces.length > 0);
}

export const menuAdmin = (yo: Yo | null): GrupoMenu[] => filtrar(ADMIN, yo);
export const menuPortal = (yo: Yo | null): GrupoMenu[] => filtrar(PORTAL, yo);

const OTRAS: Record<string, string> = {
  '/': 'Página de inicio',
  '/informacion': 'Información pública',
  '/iniciar-sesion': 'Iniciar sesión',
  '/registro': 'Registro',
  '/activar-cuenta': 'Activar cuenta',
  '/configuracion/perfil': 'Perfil',
  '/configuracion/contrasena': 'Cambiar contraseña',
  '/configuracion/sesiones': 'Sesiones',
  '/configuracion/cuenta': 'Cerrar cuenta',
  '/app/depositos': 'Depositar dinero',
  '/app/retiros': 'Retirar dinero',
  '/areas': 'Mis áreas y cargos',
};

/** Nombre legible de una ruta anotada en el uso de la aplicación (ver UsoService). */
export function tituloDeRuta(ruta: string): string {
  for (const grupo of [...ADMIN, ...PORTAL]) {
    for (const e of grupo.enlaces) {
      const destino = e.informe ? `${e.ruta}/${e.informe.toLowerCase()}` : e.ruta;
      if (destino === ruta) {
        return e.informe ? `${e.titulo} (informe)` : `${e.titulo}${grupo.titulo === 'Área de socios' ? ' (socios)' : ''}`;
      }
    }
  }
  return OTRAS[ruta] ?? ruta;
}
