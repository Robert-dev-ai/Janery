import { Component, computed, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CircleUser, KeyRound, LogOut, LucideAngularModule, MonitorSmartphone, Moon, Sun, User } from 'lucide-angular';

import { AuthService } from '../core/auth/auth';
import { FotoPerfilService } from '../core/services/foto-perfil';
import { TemaService } from '../core/services/tema';
import { rolDe } from '../shared/utils/etiquetas';

@Component({
  selector: 'app-menu-configuracion',
  imports: [LucideAngularModule, RouterLink],
  host: { class: 'ajustes' },
  template: `
    <button
      #boton
      type="button"
      class="ajustes-boton"
      aria-label="Configuración"
      title="Configuración"
      aria-haspopup="menu"
      [attr.aria-expanded]="abierto()"
      (click)="abierto.set(!abierto())"
    >
      @if (foto.url(); as url) {
        <img class="avatar" [src]="url" alt="" />
      } @else {
        <lucide-icon [img]="iconos.CircleUser" [size]="26" aria-hidden="true" />
      }
    </button>
    @if (abierto()) {
      <div class="ajustes-menu" role="menu" aria-label="Configuración">
        <div class="ajustes-correo">
          @if (auth.yo()?.nombre) { <p class="m-0 font-semibold text-texto">{{ auth.yo()?.nombre }}</p> }
          <p class="m-0 truncate">{{ auth.yo()?.correo }}</p>
          <p class="ajustes-rol">{{ rol() }}</p>
        </div>
        <a role="menuitem" routerLink="/configuracion/perfil" (click)="abierto.set(false)">
          <lucide-icon [img]="iconos.User" [size]="18" aria-hidden="true" /> Perfil
        </a>
        <a role="menuitem" routerLink="/configuracion/contrasena" (click)="abierto.set(false)">
          <lucide-icon [img]="iconos.KeyRound" [size]="18" aria-hidden="true" /> Cambiar contraseña
        </a>
        <a role="menuitem" routerLink="/configuracion/sesiones" (click)="abierto.set(false)">
          <lucide-icon [img]="iconos.MonitorSmartphone" [size]="18" aria-hidden="true" /> Sesiones
        </a>
        <button type="button" role="menuitemcheckbox" [attr.aria-checked]="tema.oscuro()" (click)="tema.alternar()">
          <lucide-icon [img]="tema.oscuro() ? iconos.Sun : iconos.Moon" [size]="18" aria-hidden="true" />
          {{ tema.oscuro() ? 'Modo claro' : 'Modo oscuro' }}
        </button>
        <button type="button" role="menuitem" (click)="salir()">
          <lucide-icon [img]="iconos.LogOut" [size]="18" aria-hidden="true" /> Cerrar sesión
        </button>
      </div>
    }
  `,
})
export class MenuConfiguracion {
  protected readonly auth = inject(AuthService);
  protected readonly tema = inject(TemaService);
  protected readonly foto = inject(FotoPerfilService);
  private readonly router = inject(Router);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly boton = viewChild.required<ElementRef<HTMLButtonElement>>('boton');

  protected readonly iconos = { CircleUser, KeyRound, LogOut, MonitorSmartphone, Moon, Sun, User };
  protected readonly abierto = signal(false);
  protected readonly rol = computed(() => rolDe(this.auth.yo()));

  @HostListener('document:click', ['$event'])
  protected alHacerClic(evento: Event): void {
    if (!this.elemento.nativeElement.contains(evento.target as Node)) {
      this.abierto.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    if (this.abierto()) {
      this.abierto.set(false);
      this.boton().nativeElement.focus();
    }
  }

  protected salir(): void {
    this.abierto.set(false);
    this.auth.cerrarSesion().subscribe(() => this.router.navigateByUrl('/'));
  }
}
