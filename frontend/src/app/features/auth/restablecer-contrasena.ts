import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth';
import { VerClave } from '../../shared/components/ver-clave';
import { mensajeDeError } from '../../shared/utils/errores';

/** Se llega con el enlace del correo de recuperación: elige la contraseña nueva, que también desbloquea el acceso. */
@Component({
  selector: 'app-restablecer-contrasena',
  imports: [FormsModule, RouterLink, VerClave],
  template: `
    <div class="caja-acceso">
      <h1>Elegir una contraseña nueva</h1>
      @if (listo()) {
        <section class="tarjeta">
          <p class="exito" role="status">Contraseña cambiada.</p>
          <p>Ya puede entrar con su contraseña nueva. Si su acceso estaba bloqueado, quedó desbloqueado.</p>
          <a routerLink="/iniciar-sesion">Iniciar sesión</a>
        </section>
      } @else if (!token) {
        <section class="tarjeta">
          <p class="error" role="alert">Este enlace está incompleto. Ábralo tal como llegó en el correo, o pida otro.</p>
          <a routerLink="/recuperar-contrasena">Pedir otro enlace</a>
        </section>
      } @else {
        <form class="tarjeta" (ngSubmit)="enviar()">
          <label>Contraseña nueva (mínimo 8 caracteres)
            <input type="password" name="nueva" autocomplete="new-password" maxlength="72" appVerClave [(ngModel)]="nueva" />
          </label>
          <label>Repita la contraseña nueva
            <input type="password" name="repetida" autocomplete="new-password" maxlength="72" appVerClave [(ngModel)]="repetida" />
          </label>
          @if (nueva && repetida && nueva !== repetida) { <p class="error" role="alert">Las dos contraseñas no coinciden.</p> }
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
            <p><a routerLink="/recuperar-contrasena">Pedir otro enlace</a></p>
          }
          <button type="submit" [disabled]="nueva.length < 8 || nueva !== repetida || enviando()">Guardar contraseña</button>
        </form>
      }
    </div>
  `,
})
export class RestablecerContrasena implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);

  protected token = '';
  protected nueva = '';
  protected repetida = '';
  protected readonly listo = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.token = this.ruta.snapshot.queryParamMap.get('token') ?? '';
    // Fuera de la barra de direcciones: no queda en el historial ni se comparte por error.
    if (this.token) {
      history.replaceState(history.state, '', location.pathname);
    }
  }

  protected enviar(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.auth.restablecerConEnlace(this.token, this.nueva).subscribe({
      next: () => this.listo.set(true),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
