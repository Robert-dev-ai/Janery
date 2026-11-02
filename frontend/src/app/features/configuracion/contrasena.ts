import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

import { AuthService } from '../../core/auth/auth';
import { mensajeDeError } from '../../shared/utils/errores';

function coinciden(grupo: AbstractControl): ValidationErrors | null {
  return grupo.get('nueva')?.value === grupo.get('confirmacion')?.value ? null : { noCoinciden: true };
}

@Component({
  selector: 'app-contrasena',
  imports: [ReactiveFormsModule],
  template: `
    <form class="tarjeta" [formGroup]="form" (ngSubmit)="enviar()">
      <h2>Cambiar contraseña</h2>
      <label>Contraseña actual
        <input type="password" formControlName="actual" autocomplete="current-password" />
      </label>
      <label>Contraseña nueva (mínimo 8 caracteres)
        <input type="password" formControlName="nueva" autocomplete="new-password" />
      </label>
      <label>Repita la contraseña nueva
        <input type="password" formControlName="confirmacion" autocomplete="new-password" />
      </label>
      @if (form.hasError('noCoinciden') && form.controls.confirmacion.dirty) {
        <p class="error" role="alert">Las contraseñas no coinciden.</p>
      }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (cambiada()) { <p class="exito" role="status">Contraseña cambiada. Se cerraron sus demás sesiones.</p> }
      <button type="submit" [disabled]="form.invalid || enviando()">Cambiar contraseña</button>
    </form>
  `,
})
export class Contrasena {
  private readonly auth = inject(AuthService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    actual: ['', [Validators.required, Validators.maxLength(72)]],
    nueva: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmacion: ['', Validators.required],
  }, { validators: coinciden });
  protected readonly error = signal<string | null>(null);
  protected readonly cambiada = signal(false);
  protected readonly enviando = signal(false);

  protected enviar(): void {
    const { actual, nueva } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.cambiada.set(false);
    this.auth.cambiarContrasena(actual, nueva).subscribe({
      next: () => {
        this.form.reset();
        this.cambiada.set(true);
        this.enviando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
