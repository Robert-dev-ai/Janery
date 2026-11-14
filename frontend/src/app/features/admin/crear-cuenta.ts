import { Component, inject, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService, UsuarioCreado } from '../../core/services/api';
import { CampoTelefono } from '../../shared/components/campo-telefono';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';
import { cedulaValida, erroresDeFormulario, telefonoValido } from '../../shared/utils/validadores';

/**
 * La presidencia crea la cuenta de quien no se registra solo. Queda con su cuenta principal activa, porque la apertura se
 * cobra en efectivo en ese momento, y con una contraseña temporal que se muestra una sola vez.
 */
@Component({
  selector: 'app-crear-cuenta',
  imports: [CampoTelefono, ReactiveFormsModule],
  template: `
    <section class="seccion seccion-accion">
      <h2 class="seccion-titulo">Crear la cuenta de una persona</h2>
      @if (creado(); as c) {
        <div class="tarjeta" role="status">
          <p class="exito m-0">Cuenta de {{ nombre() }} creada. Su cuenta principal <strong>{{ c.cuenta }}</strong> ya está activa.</p>
          <small class="suave">Contraseña temporal (solo se muestra esta vez):</small>
          <p class="m-0 font-mono text-xl font-bold tracking-wider select-all">{{ c.contrasenaTemporal }}</p>
          <small class="suave block mb-2">Entra con su cédula o su correo y esta contraseña; al entrar deberá elegir una nueva.
            Ya se le pueden tramitar o registrar créditos en Créditos.</small>
          <span class="acciones">
            <button type="button" (click)="copiar(c.contrasenaTemporal)">{{ copiada() ? 'Copiada ✓' : 'Copiar' }}</button>
            <button type="button" class="secundario" (click)="otra()">Listo, ya se la di</button>
          </span>
        </div>
      } @else {
        <p class="suave">Para quien no puede registrarse solo. Al crearla se registra el pago de apertura en efectivo, por el valor vigente en Parámetros: reciba ese dinero antes de crearla.</p>
        <form class="tarjeta" [formGroup]="form" (ngSubmit)="crear()">
          <label>Correo <input type="email" formControlName="correo" autocomplete="off" /></label>
          <label>Nombres <input formControlName="nombres" autocomplete="off" maxlength="100" /></label>
          <label>Apellidos <input formControlName="apellidos" autocomplete="off" maxlength="100" /></label>
          <label>Cédula (10 números) <input formControlName="identificacion" inputmode="numeric" maxlength="10" (input)="soloNumeros($event)" /></label>
          <label for="telefono-crear-cuenta">Teléfono</label>
          <app-campo-telefono class="mb-3 block" idCampo="telefono-crear-cuenta" formControlName="telefono" />
          <label>Dirección (opcional) <input formControlName="direccion" autocomplete="off" maxlength="200" /></label>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <div class="pie-formulario">
            <span class="acciones">
              <button type="submit" [disabled]="enviando()">Crear cuenta</button>
              <button type="button" class="secundario" (click)="cerrar.emit()">Cancelar</button>
            </span>
            @if (errores().length) {
              <ul class="errores-formulario" role="alert">@for (e of errores(); track e) { <li>{{ e }}</li> }</ul>
            }
          </div>
        </form>
      }
    </section>
  `,
})
export class CrearCuentaForm {
  readonly creada = output<void>();
  readonly cerrar = output<void>();

  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  private static readonly conTexto = [Validators.required, Validators.pattern(/\S/)];
  private static readonly ETIQUETAS: Record<string, string> = {
    correo: 'El correo', nombres: 'Los nombres', apellidos: 'Los apellidos', identificacion: 'La cédula', telefono: 'El teléfono',
  };

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    nombres: ['', CrearCuentaForm.conTexto],
    apellidos: ['', CrearCuentaForm.conTexto],
    identificacion: ['', [Validators.required, cedulaValida]],
    telefono: ['', [Validators.required, telefonoValido]],
    direccion: [''],
  });
  protected readonly creado = signal<UsuarioCreado | null>(null);
  protected readonly nombre = signal('');
  protected readonly copiada = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly intentado = signal(false);

  protected errores(): string[] {
    return erroresDeFormulario(this.form, CrearCuentaForm.ETIQUETAS, this.intentado());
  }

  protected soloNumeros(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const limpio = campo.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== campo.value) {
      campo.value = limpio;
      this.form.controls.identificacion.setValue(limpio);
    }
  }

  protected async crear(): Promise<void> {
    this.intentado.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const d = this.form.getRawValue();
    const datos = { ...d, correo: d.correo.trim(), nombres: d.nombres.trim(), apellidos: d.apellidos.trim(), direccion: d.direccion.trim() };
    const nombre = `${datos.nombres} ${datos.apellidos}`;
    const pregunta = `¿Crear la cuenta de ${nombre} (cédula ${datos.identificacion})? Se registra que pagó la apertura en efectivo y su cuenta principal queda activa.`;
    if (!(await this.dialogo.confirmar(pregunta, 'Crear cuenta'))) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.api.crearUsuario(datos).subscribe({
      next: (c) => {
        this.enviando.set(false);
        this.nombre.set(nombre);
        this.copiada.set(false);
        this.creado.set(c);
        this.creada.emit();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected copiar(clave: string): void {
    navigator.clipboard.writeText(clave).then(
      () => this.copiada.set(true),
      () => this.error.set('No se pudo copiar: seleccione la contraseña y cópiela a mano.'),
    );
  }

  /** Limpia el formulario para crear la siguiente. */
  protected otra(): void {
    this.creado.set(null);
    this.intentado.set(false);
    this.form.reset();
    this.cerrar.emit();
  }
}
