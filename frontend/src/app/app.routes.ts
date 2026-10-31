import { Routes } from '@angular/router';

import { autenticadoGuard, cuentaActivaGuard, permisoGuard } from './core/auth/guards';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
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
        path: 'activar-cuenta',
        canActivate: [autenticadoGuard],
        loadComponent: () => import('./features/cuenta/activar-cuenta').then((m) => m.ActivarCuenta),
      },
      {
        path: 'app',
        canActivate: [cuentaActivaGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'inicio' },
          { path: 'inicio', redirectTo: 'cuenta' },
          { path: 'cuenta', loadComponent: () => import('./features/cuenta/mi-cuenta').then((m) => m.MiCuenta) },
          { path: 'movimientos', loadComponent: () => import('./features/cuenta/movimientos').then((m) => m.Movimientos) },
          { path: 'perfil', loadComponent: () => import('./features/cuenta/perfil').then((m) => m.PerfilPagina) },
          { path: 'creditos', loadComponent: () => import('./features/creditos/creditos').then((m) => m.Creditos) },
          { path: 'ahorros', loadComponent: () => import('./features/ahorros/ahorros').then((m) => m.Ahorros) },
          { path: 'inversiones', loadComponent: () => import('./features/socios/inversiones').then((m) => m.Inversiones) },
        ],
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
        canActivate: [autenticadoGuard],
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
        path: 'areas/tesoreria',
        canActivate: [permisoGuard('CONSULTAR_CAJA')],
        loadComponent: () => import('./features/admin/tesoreria').then((m) => m.AreaTesoreria),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
