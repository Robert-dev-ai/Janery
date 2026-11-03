import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth';

@Component({
  selector: 'app-configuracion',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <h1>Configuración</h1>
    <nav class="pestanas" aria-label="Configuración">
      <a routerLink="perfil" routerLinkActive="activo">Perfil</a>
      <a routerLink="contrasena" routerLinkActive="activo">Contraseña</a>
      <a routerLink="sesiones" routerLinkActive="activo">Sesiones</a>
      @if (auth.yo()?.estadoCuenta === 'ACTIVA') {
        <a routerLink="cuenta" routerLinkActive="activo">Cuenta</a>
      }
    </nav>
    <div class="estrecho"><router-outlet /></div>
  `,
})
export class Configuracion {
  protected readonly auth = inject(AuthService);
}
