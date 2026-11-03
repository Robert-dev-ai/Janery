import { Routes } from '@angular/router';

import { autenticadoGuard, cuentaActivaGuard, permisoGuard, personalGuard, socioGuard } from './core/auth/guards';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/layout-publico').then((m) => m.LayoutPublico),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/public/inicio/inicio').then((m) => m.Inicio),
      },
      {
        path: 'informacion',
        loadComponent: () => import('./features/public/informacion').then((m) => m.Informacion),
      },
      {
        path: 'registro',
        loadComponent: () => import('./features/auth/registro').then((m) => m.Registro),
      },
      {
        path: 'iniciar-sesion',
        loadComponent: () => import('./features/auth/iniciar-sesion').then((m) => m.IniciarSesion),
      },
      {
        path: 'recuperar-contrasena',
        loadComponent: () => import('./features/auth/recuperar-contrasena').then((m) => m.RecuperarContrasena),
      },
    ],
  },
  {
    path: '',
    canActivate: [autenticadoGuard],
    data: { experiencia: 'portal' },
    loadComponent: () => import('./layout/layout-panel').then((m) => m.LayoutPanel),
    children: [
      {
        path: 'mis-cargos',
        loadComponent: () => import('./features/areas/areas').then((m) => m.Areas),
      },
      {
        path: 'activar-cuenta',
        loadComponent: () => import('./features/cuenta/activar-cuenta').then((m) => m.ActivarCuenta),
      },
      {
        path: 'configuracion',
        loadComponent: () => import('./features/configuracion/configuracion').then((m) => m.Configuracion),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'perfil' },
          { path: 'perfil', loadComponent: () => import('./features/configuracion/perfil').then((m) => m.PerfilPagina) },
          { path: 'contrasena', loadComponent: () => import('./features/configuracion/contrasena').then((m) => m.Contrasena) },
          { path: 'sesiones', loadComponent: () => import('./features/configuracion/sesiones').then((m) => m.Sesiones) },
          { path: 'cuenta', loadComponent: () => import('./features/configuracion/cerrar-cuenta').then((m) => m.CerrarCuenta) },
        ],
      },
      {
        path: 'app',
        canActivate: [cuentaActivaGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'inicio' },
          { path: 'inicio', loadComponent: () => import('./features/cuenta/resumen').then((m) => m.Resumen) },
          { path: 'cuenta', redirectTo: 'inicio' },
          { path: 'pagar', loadComponent: () => import('./features/pagos/pagar').then((m) => m.Pagar) },
          { path: 'movimientos', loadComponent: () => import('./features/cuenta/movimientos').then((m) => m.Movimientos) },
          { path: 'retiros', loadComponent: () => import('./features/cuenta/retiros').then((m) => m.Retiros) },
          { path: 'depositos', loadComponent: () => import('./features/cuenta/depositos').then((m) => m.Depositos) },
          { path: 'perfil', redirectTo: '/configuracion/perfil' },
          { path: 'creditos', loadComponent: () => import('./features/creditos/creditos').then((m) => m.Creditos) },
          { path: 'ahorros', loadComponent: () => import('./features/ahorros/ahorros').then((m) => m.Ahorros) },
          { path: 'inversiones', loadComponent: () => import('./features/socios/inversiones').then((m) => m.Inversiones) },
          {
            path: 'socios/general',
            canActivate: [socioGuard],
            loadComponent: () => import('./features/socios/area-general').then((m) => m.AreaGeneral),
          },
          { path: 'hacerme-socio', loadComponent: () => import('./features/socios/hacerme-socio').then((m) => m.HacermeSocio) },
          // Finanzas para los socios: las mismas pantallas, solo para ver.
          {
            path: 'socios',
            canActivate: [socioGuard],
            children: [
              { path: 'balance', loadComponent: () => import('./features/contabilidad/balance').then((m) => m.Balance) },
              { path: 'diario', loadComponent: () => import('./features/contabilidad/libro-diario').then((m) => m.LibroDiarioPagina) },
              { path: 'tesoreria', loadComponent: () => import('./features/admin/tesoreria').then((m) => m.AreaTesoreria) },
              { path: 'estado-caja', loadComponent: () => import('./features/admin/estado-caja').then((m) => m.EstadoCaja) },
              { path: 'caja-chica', loadComponent: () => import('./features/admin/caja-chica').then((m) => m.AreaCajaChica) },
              { path: 'resultados', loadComponent: () => import('./features/admin/resultados').then((m) => m.AdminResultados) },
            ],
          },
        ],
      },
    ],
  },
  {
    path: '',
    canActivate: [autenticadoGuard],
    data: { experiencia: 'admin' },
    loadComponent: () => import('./layout/layout-panel').then((m) => m.LayoutPanel),
    children: [
      {
        path: 'admin/panel',
        canActivate: [personalGuard],
        loadComponent: () => import('./features/admin/panel').then((m) => m.AdminPanel),
      },
      {
        path: 'admin/solicitudes-deposito',
        canActivate: [permisoGuard('GESTIONAR_CUENTAS')],
        loadComponent: () => import('./features/admin/solicitudes-deposito').then((m) => m.SolicitudesDeposito),
      },
      {
        path: 'admin/solicitudes-retiro',
        canActivate: [permisoGuard('GESTIONAR_CUENTAS')],
        loadComponent: () => import('./features/admin/solicitudes-retiro').then((m) => m.SolicitudesRetiro),
      },
      {
        path: 'admin/personas',
        canActivate: [permisoGuard('ADMIN_USUARIOS')],
        loadComponent: () => import('./features/admin/personas').then((m) => m.AdminPersonas),
      },
      {
        path: 'admin/historial-crediticio',
        canActivate: [permisoGuard('APROBAR_CREDITOS')],
        loadComponent: () => import('./features/admin/historial-crediticio').then((m) => m.HistorialCrediticioPagina),
      },
      {
        path: 'admin/contabilidad/balance',
        canActivate: [permisoGuard('CONSULTAR_CONTABILIDAD')],
        loadComponent: () => import('./features/contabilidad/balance').then((m) => m.Balance),
      },
      {
        path: 'admin/contabilidad/diario',
        canActivate: [permisoGuard('CONSULTAR_CONTABILIDAD')],
        loadComponent: () => import('./features/contabilidad/libro-diario').then((m) => m.LibroDiarioPagina),
      },
      {
        path: 'admin/contabilidad/mayor',
        canActivate: [permisoGuard('CONSULTAR_CONTABILIDAD')],
        loadComponent: () => import('./features/contabilidad/libro-mayor').then((m) => m.LibroMayorPagina),
      },
      {
        path: 'admin/contabilidad/registro',
        canActivate: [permisoGuard('REGISTRAR_ASIENTOS')],
        loadComponent: () => import('./features/contabilidad/registro-manual').then((m) => m.RegistroManual),
      },
      {
        path: 'admin/cuentas',
        canActivate: [permisoGuard('GESTIONAR_CUENTAS')],
        loadComponent: () => import('./features/admin/cuentas').then((m) => m.AdminCuentas),
      },
      {
        path: 'admin/creditos',
        canActivate: [permisoGuard('APROBAR_CREDITOS')],
        loadComponent: () => import('./features/admin/creditos').then((m) => m.AdminCreditos),
      },
      {
        path: 'admin/uso',
        canActivate: [permisoGuard('VER_USO_APP')],
        loadComponent: () => import('./features/admin/uso').then((m) => m.UsoAplicacion),
      },
      {
        path: 'admin/simulador',
        canActivate: [permisoGuard('USAR_SIMULADOR')],
        loadComponent: () => import('./features/admin/simulador').then((m) => m.Simulador),
      },
      {
        path: 'admin/parametros',
        canActivate: [permisoGuard('ADMIN_PARAMETROS')],
        loadComponent: () => import('./features/admin/parametros').then((m) => m.AdminParametros),
      },
      {
        path: 'admin/usuarios',
        canActivate: [permisoGuard('ADMIN_USUARIOS')],
        loadComponent: () => import('./features/admin/usuarios').then((m) => m.AdminUsuarios),
      },
      {
        path: 'admin/auditoria',
        canActivate: [permisoGuard('CONSULTAR_AUDITORIA')],
        loadComponent: () => import('./features/admin/auditoria').then((m) => m.AdminAuditoria),
      },
      {
        path: 'areas',
        pathMatch: 'full',
        loadComponent: () => import('./features/areas/areas').then((m) => m.Areas),
      },
      {
        path: 'admin/roles-permisos',
        canActivate: [permisoGuard('ADMIN_USUARIOS')],
        loadComponent: () => import('./features/admin/roles-permisos').then((m) => m.RolesPermisos),
      },
      {
        path: 'admin/reportes',
        canActivate: [permisoGuard('CONSULTAR_INFORMES')],
        loadComponent: () => import('./features/admin/reportes').then((m) => m.AdminReportes),
      },
      {
        path: 'admin/retiros',
        canActivate: [permisoGuard('GESTIONAR_RESULTADOS')],
        loadComponent: () => import('./features/admin/retiros').then((m) => m.AdminRetiros),
      },
      {
        path: 'admin/resultados',
        canActivate: [permisoGuard('GESTIONAR_RESULTADOS')],
        loadComponent: () => import('./features/admin/resultados').then((m) => m.AdminResultados),
      },
      {
        path: 'areas/caja-chica',
        canActivate: [permisoGuard('GESTIONAR_CAJA_CHICA')],
        loadComponent: () => import('./features/admin/caja-chica').then((m) => m.AreaCajaChica),
      },
      {
        path: 'areas/estado-caja',
        canActivate: [permisoGuard('CONSULTAR_CAJA')],
        loadComponent: () => import('./features/admin/estado-caja').then((m) => m.EstadoCaja),
      },
      {
        path: 'areas/tesoreria',
        canActivate: [permisoGuard('CONSULTAR_CAJA')],
        loadComponent: () => import('./features/admin/tesoreria').then((m) => m.AreaTesoreria),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
