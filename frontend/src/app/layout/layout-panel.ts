import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ArrowLeftRight, LucideAngularModule, Menu } from 'lucide-angular';
import { filter, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth';
import { TiempoRealService } from '../core/services/tiempo-real';
import { PieContacto } from '../shared/components/pie-contacto';
import { WhatsappFlotante } from '../shared/components/whatsapp-flotante';
import { AnuncioCabecera } from './anuncio-cabecera';
import { AvisosPendientes } from './avisos-pendientes';
import { BarraSimulacion } from './barra-simulacion';
import { MenuConfiguracion } from './menu-configuracion';
import { GrupoMenu, menuAdmin, menuPortal } from './navegacion';

/** Marco del portal del socio y del panel administrativo; la experiencia la indica la ruta. */
@Component({
  selector: 'app-layout-panel',
  imports: [AnuncioCabecera, AvisosPendientes, BarraSimulacion, PieContacto, WhatsappFlotante, LucideAngularModule, MenuConfiguracion, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="marco">
      @if (menuAbierto()) {
        <button type="button" class="velo" aria-label="Cerrar menú" (click)="menuAbierto.set(false)"></button>
      }
      <aside class="lateral" [class.lateral-claro]="!esAdmin" [class.abierto]="menuAbierto()">
        <a class="lateral-marca" routerLink="/">
          <img src="logo-icono.png" alt="" />
          <span><strong>Janery</strong><small>{{ titulo() }}</small></span>
        </a>
        <nav [attr.aria-label]="titulo()">
          @if (destinoCambio(); as d) {
            <div class="cambio-movil">
              <a class="lateral-enlace" [routerLink]="d.ruta" (click)="menuAbierto.set(false)">
                <lucide-icon [img]="iconos.ArrowLeftRight" [size]="18" aria-hidden="true" /> Ir al {{ d.titulo.toLowerCase() }}
              </a>
            </div>
          }
          @for (grupo of grupos(); track grupo.titulo) {
            <details class="lateral-seccion" [open]="!esAdmin || $first || contieneActual(grupo)">
              <summary class="lateral-grupo">{{ grupo.titulo }}</summary>
              @for (e of grupo.enlaces; track e.titulo) {
                <a class="lateral-enlace" [class.lateral-subenlace]="e.sub" [routerLink]="e.ruta" [queryParams]="e.informe ? { informe: e.informe } : null" routerLinkActive="activo"
                  [routerLinkActiveOptions]="{ exact: e.exacto ?? false }" (click)="menuAbierto.set(false)">
                  <lucide-icon [img]="e.icono" [size]="e.sub ? 14 : 18" aria-hidden="true" /> {{ e.titulo }}
                </a>
              }
            </details>
          }
        </nav>
      </aside>

      <div class="principal">
        @if (simulacion) { <app-barra-simulacion /> }
        <header class="cabecera">
          <button type="button" class="icono-boton lg:hidden" aria-label="Abrir menú" [attr.aria-expanded]="menuAbierto()"
            (click)="menuAbierto.set(true)">
            <lucide-icon [img]="iconos.Menu" [size]="22" aria-hidden="true" />
          </button>
          <app-anuncio-cabecera />
          <div class="cabecera-acciones">
            @if (vivo.conectado()) {
              <span class="en-vivo" title="Los datos se actualizan solos"><i></i>En vivo</span>
            } @else {
              <span class="sin-conexion">Reconectando…</span>
            }
            @if (destinoCambio(); as d) {
              <a class="cambio-experiencia" [routerLink]="d.ruta" [attr.aria-label]="'Ir al ' + d.titulo.toLowerCase()" [title]="'Ir al ' + d.titulo.toLowerCase()">
                <lucide-icon [img]="iconos.ArrowLeftRight" [size]="16" aria-hidden="true" /> <span class="hidden sm:inline">{{ d.titulo }}</span>
              </a>
            }
            <app-avisos-pendientes [gestion]="esAdmin" />
            <app-menu-configuracion />
          </div>
        </header>
        <main class="contenido"><router-outlet /></main>
        <app-pie-contacto class="pie-panel" />
        <app-whatsapp-flotante />
      </div>
    </div>
  `,
})
export class LayoutPanel {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly vivo = inject(TiempoRealService);

  protected readonly simulacion = environment.simulacion;
  protected readonly iconos = { ArrowLeftRight, Menu };
  protected readonly esAdmin = inject(ActivatedRoute).snapshot.data['experiencia'] === 'admin';
  private readonly tituloPortal = computed(() => (this.auth.yo()?.esSocio ? 'Panel del socio' : 'Panel del cliente'));
  protected readonly titulo = computed(() => (this.esAdmin ? 'Panel administrativo' : this.tituloPortal()));
  protected readonly menuAbierto = signal(false);
  private readonly urlActual = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );

  protected readonly grupos = computed(() => (this.esAdmin ? menuAdmin(this.auth.yo()) : menuPortal(this.auth.yo())));

  protected readonly destinoCambio = computed(() => {
    if (this.esAdmin) {
      return { titulo: this.tituloPortal(), ruta: this.auth.rutaPortal() };
    }
    return this.auth.rutaPanel() ? { titulo: 'Panel administrativo', ruta: this.auth.rutaPanel()! } : null;
  });

  /** En el panel solo se despliega la sección de la pantalla actual, para no saturar el menú. */
  protected contieneActual(grupo: GrupoMenu): boolean {
    const url = this.urlActual();
    return grupo.enlaces.some((e) => (e.informe ? url.includes(`informe=${e.informe}`) : e.exacto ? url === e.ruta : url.startsWith(e.ruta)));
  }

  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    this.menuAbierto.set(false);
  }
}
