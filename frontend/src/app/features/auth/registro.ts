import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="estrecho">
      <h1>Registrarse</h1>
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
        <label>Correo
          <input type="email" formControlName="correo" autocomplete="email" />
        </label>
        <label>Contraseña (mínimo 8 caracteres)
          <input type="password" formControlName="contrasena" autocomplete="new-password" />
        </label>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        <button type="submit" [disabled]="form.invalid || enviando()">Crear cuenta de acceso</button>
      </form>
      <p>¿Ya tiene acceso? <a routerLink="/iniciar-sesion">Iniciar sesión</a></p>
    </div>
  `,
})
export class Registro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    contrasena: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected enviar(): void {
    const { correo, contrasena } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.registrar(correo, contrasena).pipe(
      switchMap(() => this.auth.iniciarSesion(correo, contrasena)),
    ).subscribe({
      next: () => this.router.navigateByUrl('/informacion'),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
