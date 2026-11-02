import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth';

@Component({
  selector: 'app-layout-publico',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
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
    <footer class="publico-pie">Caja Familiar Janery · Caja de ahorro y crédito</footer>
  `,
})
export class LayoutPublico {
  protected readonly auth = inject(AuthService);
}
