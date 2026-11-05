import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth';
import { VerClave } from '../../shared/components/ver-clave';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-iniciar-sesion',
  imports: [ReactiveFormsModule, RouterLink, VerClave],
  template: `
    <div class="caja-acceso">
      <h1>Iniciar sesión</h1>
      @if (aviso) { <p class="alerta-atraso" role="status">{{ aviso }}</p> }
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
        <label>Correo o cédula
          <input formControlName="correo" autocomplete="username" />
        </label>
        <label>Contraseña
          <input type="password" formControlName="contrasena" autocomplete="current-password" appVerClave />
        </label>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        <button type="submit" [disabled]="form.invalid || enviando()">Entrar</button>
        <p class="mt-3 mb-0 text-sm"><a routerLink="/recuperar-contrasena">¿Olvidó su contraseña?</a></p>
      </form>
      <p>¿Aún no tiene acceso? <a routerLink="/registro">Registrarse</a></p>
    </div>
  `,
})
export class IniciarSesion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  /** Por qué llegó aquí, si perdió la sesión mientras usaba la aplicación. */
  protected readonly aviso: string | null = (this.router.getCurrentNavigation()?.extras.state?.['aviso'] as string) ?? null;

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', Validators.required],
    contrasena: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected enviar(): void {
    const { correo, contrasena } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.iniciarSesion(correo.trim(), contrasena).subscribe({
      next: () => this.router.navigateByUrl(this.auth.rutaInicial()),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
