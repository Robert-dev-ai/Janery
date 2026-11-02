import { Component, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-sesiones',
  template: `
    <section class="tarjeta">
      <h2>Sesiones</h2>
      <p>Si inició sesión en otro dispositivo o cree que alguien más entró, ciérrelas desde aquí. Esta sesión seguirá abierta.</p>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (cerradas()) { <p class="exito" role="status">Se cerraron las demás sesiones.</p> }
      <button type="button" [disabled]="enviando()" (click)="cerrarOtras()">Cerrar las demás sesiones</button>
    </section>
  `,
})
export class Sesiones {
  private readonly auth = inject(AuthService);

  protected readonly error = signal<string | null>(null);
  protected readonly cerradas = signal(false);
  protected readonly enviando = signal(false);

  protected cerrarOtras(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.cerradas.set(false);
    this.auth.cerrarOtrasSesiones().subscribe({
      next: () => {
        this.cerradas.set(true);
        this.enviando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
