import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { CampoTelefono } from '../../shared/components/campo-telefono';
import { VerClave } from '../../shared/components/ver-clave';
import { mensajeDeError } from '../../shared/utils/errores';
import { cedulaValida, erroresDeFormulario, telefonoValido } from '../../shared/utils/validadores';

/** Un correo mal escrito deja a la persona sin poder entrar con él: se pide dos veces. */
function correosIguales(grupo: AbstractControl): ValidationErrors | null {
  const correo = String(grupo.get('correo')?.value ?? '').trim().toLowerCase();
  const confirmacion = String(grupo.get('confirmacionCorreo')?.value ?? '').trim().toLowerCase();
  return !confirmacion || correo === confirmacion ? null : { correosDistintos: true };
}

@Component({
  selector: 'app-registro',
  imports: [CampoTelefono, ReactiveFormsModule, RouterLink, VerClave],
  template: `
    <div class="caja-acceso">
      <h1>Registrarse</h1>
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
        <label>Correo
          <input type="email" formControlName="correo" autocomplete="email" />
        </label>
        <label>Repita su correo
          <input type="email" formControlName="confirmacionCorreo" autocomplete="off" (paste)="$event.preventDefault()" />
        </label>
        <label>Nombres
          <input formControlName="nombres" autocomplete="given-name" maxlength="100" />
        </label>
        <label>Apellidos
          <input formControlName="apellidos" autocomplete="family-name" maxlength="100" />
        </label>
        <label>Cédula (10 números)
          <input formControlName="identificacion" inputmode="numeric" maxlength="10" (input)="soloNumeros($event, 'identificacion')" />
        </label>
        <label for="telefono-registro">Teléfono</label>
        <app-campo-telefono class="mb-3 block" idCampo="telefono-registro" formControlName="telefono" />
        <label>Contraseña (mínimo 8 caracteres)
          <input type="password" formControlName="contrasena" autocomplete="new-password" appVerClave />
        </label>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        <div class="pie-formulario">
          <button type="submit" [disabled]="enviando()">Crear cuenta de acceso</button>
          @if (errores().length) {
            <ul class="errores-formulario" role="alert">
              @for (e of errores(); track e) { <li>{{ e }}</li> }
            </ul>
          }
        </div>
      </form>
      <p>¿Ya tiene acceso? <a routerLink="/iniciar-sesion">Iniciar sesión</a></p>
    </div>
  `,
})
export class Registro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private static readonly conTexto = [Validators.required, Validators.pattern(/\S/)];
  private static readonly ETIQUETAS: Record<string, string> = {
    correo: 'El correo', confirmacionCorreo: 'La confirmación del correo', nombres: 'Los nombres', apellidos: 'Los apellidos',
    identificacion: 'La cédula', telefono: 'El teléfono', contrasena: 'La contraseña',
  };

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    confirmacionCorreo: ['', Validators.required],
    nombres: ['', Registro.conTexto],
    apellidos: ['', Registro.conTexto],
    identificacion: ['', [Validators.required, cedulaValida]],
    telefono: ['', [Validators.required, telefonoValido]],
    contrasena: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  }, { validators: correosIguales });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  private readonly intentado = signal(false);

  protected errores(): string[] {
    const lista = erroresDeFormulario(this.form, Registro.ETIQUETAS, this.intentado());
    if (this.form.hasError('correosDistintos')) {
      lista.push('Los dos correos no coinciden');
    }
    return lista;
  }

  protected soloNumeros(evento: Event, campo: 'identificacion'): void {
    const entrada = evento.target as HTMLInputElement;
    const limpio = entrada.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== entrada.value) {
      entrada.value = limpio;
      this.form.controls[campo].setValue(limpio);
    }
  }

  protected enviar(): void {
    this.intentado.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { confirmacionCorreo: _, ...datos } = this.form.getRawValue();
    const limpio = { ...datos, correo: datos.correo.trim(), nombres: datos.nombres.trim(), apellidos: datos.apellidos.trim() };
    this.enviando.set(true);
    this.error.set(null);
    this.auth.registrar(limpio).pipe(
      switchMap(() => this.auth.iniciarSesion(limpio.correo, limpio.contrasena)),
    ).subscribe({
      next: () => this.router.navigateByUrl(this.auth.rutaInicial()),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
