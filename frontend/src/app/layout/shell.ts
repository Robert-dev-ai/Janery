import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth';
import { TiempoRealService } from '../core/services/tiempo-real';
import { environment } from '../../environments/environment';
import { BarraSimulacion } from './barra-simulacion';

@Component({
  selector: 'app-shell',
  imports: [BarraSimulacion, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    @if (simulacion && auth.autenticado()) {
      <app-barra-simulacion />
    }
    <header class="barra">
      <a class="marca" routerLink="/">Janery</a>
      <nav aria-label="Principal">
        <a routerLink="/informacion" routerLinkActive="activo">Información</a>
        @if (auth.autenticado()) {
          @if (auth.yo()?.estadoCuenta === 'ACTIVA') {
            <a routerLink="/app/cuenta" routerLinkActive="activo">Mi cuenta</a>
            <a routerLink="/app/movimientos" routerLinkActive="activo">Movimientos</a>
            <a routerLink="/app/creditos" routerLinkActive="activo">Créditos</a>
            <a routerLink="/app/ahorros" routerLinkActive="activo">Ahorros</a>
            @if (auth.yo()?.esSocio) {
              <a routerLink="/app/inversiones" routerLinkActive="activo">Mi fondo personal</a>
            }
            <a routerLink="/app/perfil" routerLinkActive="activo">Perfil</a>
          } @else {
            <a routerLink="/activar-cuenta" routerLinkActive="activo">Activar cuenta</a>
          }
          @if (auth.yo()?.cargos?.length) {
            <a routerLink="/areas" [routerLinkActiveOptions]="{ exact: true }" routerLinkActive="activo">Mis áreas</a>
          }
          @if (auth.tienePermiso('CONSULTAR_INFORMES')) {
            <a routerLink="/admin/reportes" routerLinkActive="activo">Informes</a>
          }
          @if (auth.tienePermiso('GESTIONAR_CUENTAS')) {
            <a routerLink="/admin/cuentas" routerLinkActive="activo">Cuentas</a>
          }
          @if (auth.tienePermiso('APROBAR_CREDITOS')) {
            <a routerLink="/admin/creditos" routerLinkActive="activo">Aprobar créditos</a>
          }
          @if (auth.tienePermiso('CONSULTAR_CAJA')) {
            <a routerLink="/areas/tesoreria" routerLinkActive="activo">Tesorería</a>
          }
          @if (auth.tienePermiso('GESTIONAR_CAJA_CHICA')) {
            <a routerLink="/areas/caja-chica" routerLinkActive="activo">Caja chica</a>
          }
          @if (auth.tienePermiso('GESTIONAR_RESULTADOS')) {
            <a routerLink="/admin/resultados" routerLinkActive="activo">Resultados</a>
            <a routerLink="/admin/retiros" routerLinkActive="activo">Retiros de socios</a>
          }
          @if (auth.tienePermiso('ADMIN_PARAMETROS')) {
            <a routerLink="/admin/parametros" routerLinkActive="activo">Parámetros</a>
          }
          @if (auth.tienePermiso('ADMIN_USUARIOS')) {
            <a routerLink="/admin/usuarios" routerLinkActive="activo">Usuarios</a>
          }
          @if (auth.tienePermiso('ADMIN_USUARIOS')) {
            <a routerLink="/admin/roles-permisos" routerLinkActive="activo">Permisos</a>
          }
          @if (auth.tienePermiso('CONSULTAR_AUDITORIA')) {
            <a routerLink="/admin/auditoria" routerLinkActive="activo">Auditoría</a>
          }
          @if (vivo.conectado()) {
            <span class="en-vivo" title="Los datos se actualizan solos"><i></i>En vivo</span>
          } @else {
            <span class="sin-conexion">Reconectando…</span>
          }
          <button type="button" class="secundario" (click)="salir()">Cerrar sesión</button>
        } @else {
          <a routerLink="/iniciar-sesion" routerLinkActive="activo">Iniciar sesión</a>
          <a routerLink="/registro" routerLinkActive="activo">Registrarse</a>
        }
      </nav>
    </header>
    <main class="contenido"><router-outlet /></main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly vivo = inject(TiempoRealService);
  protected readonly simulacion = environment.simulacion;
  private readonly router = inject(Router);

  protected salir(): void {
    this.auth.cerrarSesion().subscribe(() => this.router.navigateByUrl('/'));
  }
}
