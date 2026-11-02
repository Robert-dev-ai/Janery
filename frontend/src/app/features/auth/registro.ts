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
    <div class="caja-acceso">
      <h1>Registrarse</h1>
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
        <label>Correo
          <input type="email" formControlName="correo" autocomplete="email" />
        </label>
        <label>Nombres
          <input formControlName="nombres" autocomplete="given-name" maxlength="100" />
        </label>
        <label>Apellidos
          <input formControlName="apellidos" autocomplete="family-name" maxlength="100" />
        </label>
        <label>Cédula o identificación
          <input formControlName="identificacion" maxlength="20" />
        </label>
        <label>Teléfono
          <input type="tel" formControlName="telefono" autocomplete="tel" maxlength="20" />
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

  private static readonly conTexto = [Validators.required, Validators.pattern(/\S/)];

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    nombres: ['', Registro.conTexto],
    apellidos: ['', Registro.conTexto],
    identificacion: ['', [...Registro.conTexto, Validators.pattern(/^[^@]+$/)]],
    telefono: ['', Registro.conTexto],
    contrasena: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected enviar(): void {
    const datos = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.auth.registrar(datos).pipe(
      switchMap(() => this.auth.iniciarSesion(datos.correo, datos.contrasena)),
    ).subscribe({
      next: () => this.router.navigateByUrl(this.auth.rutaInicial()),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
