import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-iniciar-sesion',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="estrecho">
      <h1>Iniciar sesión</h1>
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
        <label>Correo
          <input type="email" formControlName="correo" autocomplete="username" />
        </label>
        <label>Contraseña
          <input type="password" formControlName="contrasena" autocomplete="current-password" />
        </label>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        <button type="submit" [disabled]="form.invalid || enviando()">Entrar</button>
      </form>
      <p>¿Aún no tiene acceso? <a routerLink="/registro">Registrarse</a></p>
    </div>
  `,
})
export class IniciarSesion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    contrasena: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected enviar(): void {
    const { correo, contrasena } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.iniciarSesion(correo, contrasena).subscribe({
      next: () => this.router.navigateByUrl(this.auth.rutaInicial()),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
