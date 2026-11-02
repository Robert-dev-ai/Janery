import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth';
import { PieContacto } from '../shared/components/pie-contacto';
import { WhatsappFlotante } from '../shared/components/whatsapp-flotante';

@Component({
  selector: 'app-layout-publico',
  imports: [PieContacto, RouterLink, RouterLinkActive, RouterOutlet, WhatsappFlotante],
  host: { class: 'publico' },
  template: `
    <header class="publico-barra">
      <div>
        <a class="marca" routerLink="/"><img src="logo-icono.png" alt="" />Caja Familiar Janery</a>
        <nav aria-label="Principal">
          <a routerLink="/informacion" routerLinkActive="activo">Información</a>
          @if (auth.autenticado()) {
            <a class="boton" [routerLink]="auth.rutaInicial()">Ir a mi cuenta</a>
          } @else {
            <a routerLink="/iniciar-sesion" routerLinkActive="activo">Iniciar sesión</a>
            <a class="boton" routerLink="/registro">Registrarse</a>
          }
        </nav>
      </div>
    </header>
    <main class="publico-contenido"><router-outlet /></main>
    <app-pie-contacto class="publico-pie" />
    <app-whatsapp-flotante />
  `,
})
export class LayoutPublico {
  protected readonly auth = inject(AuthService);
}
