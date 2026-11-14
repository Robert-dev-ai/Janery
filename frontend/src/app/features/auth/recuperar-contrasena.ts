import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth';
import { mensajeDeError } from '../../shared/utils/errores';

/**
 * Con el correo configurado, le llega a la persona un enlace para elegir una contraseña nueva. Sin él, el pedido le llega
 * a la presidencia, que entrega una contraseña temporal en persona o por mensaje; al entrar con ella se pide elegir otra.
 */
@Component({
  selector: 'app-recuperar-contrasena',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="caja-acceso">
      <h1>Recuperar contraseña</h1>
      @if (enviado(); as medio) {
        <section class="tarjeta">
          <p class="exito" role="status">Pedido enviado.</p>
          @if (medio === 'CORREO') {
            <p>Si sus datos están registrados, le enviamos un correo con un enlace para elegir una contraseña nueva. Vence en 30 minutos y sirve una sola vez.</p>
            <p class="suave">Si no lo ve en unos minutos, revise la carpeta de spam o correo no deseado.</p>
          } @else {
            <p>Si sus datos están registrados, la presidencia recibió su pedido. Le entregará una contraseña temporal; al entrar con ella le pediremos elegir una nueva.</p>
          }
          <p class="suave">Esto también desbloquea su acceso si se bloqueó por escribir mal la contraseña 3 veces.</p>
          <a routerLink="/iniciar-sesion">Volver a iniciar sesión</a>
        </section>
      } @else {
        <form class="tarjeta" (ngSubmit)="enviar()">
          <p class="suave">Escriba el correo o la cédula con que se registró.</p>
          <label>Correo o cédula <input name="identificador" autocomplete="username" maxlength="254" [(ngModel)]="identificador" /></label>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <button type="submit" [disabled]="!identificador.trim() || enviando()">Pedir una contraseña nueva</button>
        </form>
        <p><a routerLink="/iniciar-sesion">Volver a iniciar sesión</a></p>
      }
    </div>
  `,
})
export class RecuperarContrasena {
  private readonly auth = inject(AuthService);

  protected identificador = '';
  protected readonly enviado = signal<'CORREO' | 'PRESIDENCIA' | null>(null);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected enviar(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.auth.pedirRestablecimiento(this.identificador.trim()).subscribe({
      next: (r) => this.enviado.set(r?.medio ?? 'PRESIDENCIA'),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
