import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { switchMap } from 'rxjs';

import { ApiService, DatosCredito, Perfil } from '../../core/services/api';
import { CampoTelefono } from '../../shared/components/campo-telefono';
import { mensajeDeError } from '../../shared/utils/errores';
import { erroresDeFormulario, fechaMayorDeEdad, mayorDeEdad, telefonoValido } from '../../shared/utils/validadores';

const conTexto = [Validators.required, Validators.pattern(/\S/)];

/**
 * Dirección y datos para evaluar un crédito, en grupos separados: trabajo, datos personales, referencia y cuenta
 * bancaria. Llega lleno con lo ya guardado; la persona solo corrige lo que haya cambiado.
 */
@Component({
  selector: 'app-form-datos-credito',
  imports: [CampoTelefono, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="guardar()">
      @if (perfil()?.datosCreditoCompletos) {
        <p class="alerta">Estos son los datos que tenemos guardados. Si algo cambió, corríjalo antes de continuar.</p>
      }
      <fieldset class="grupo-campos">
        <legend>Domicilio</legend>
        <label>Dirección <input formControlName="direccion" autocomplete="street-address" maxlength="200" /></label>
      </fieldset>
      <fieldset class="grupo-campos">
        <legend>Trabajo e ingresos</legend>
        <label>Lugar de trabajo <input formControlName="lugarTrabajo" maxlength="150" /></label>
        <label>Ocupación <input formControlName="ocupacion" maxlength="100" /></label>
        <label>Antigüedad en el trabajo <input formControlName="antiguedadTrabajo" maxlength="50" placeholder="Ejemplo: 2 años" /></label>
        <label>Ingresos mensuales (USD) <input type="number" min="0" step="1" formControlName="ingresosMensuales" /></label>
      </fieldset>
      <fieldset class="grupo-campos">
        <legend>Datos personales</legend>
        <label>Fecha de nacimiento (debe ser mayor de edad)
          <input type="date" formControlName="fechaNacimiento" [max]="fechaMaxima" />
        </label>
        <label>Estado civil
          <select formControlName="estadoCivil">
            <option value="SOLTERO">Soltero/a</option>
            <option value="CASADO">Casado/a</option>
            <option value="UNION_LIBRE">Unión libre</option>
            <option value="DIVORCIADO">Divorciado/a</option>
            <option value="VIUDO">Viudo/a</option>
          </select>
        </label>
      </fieldset>
      <fieldset class="grupo-campos">
        <legend>Referencia personal</legend>
        <p class="suave">Una persona que lo conozca y a quien podamos llamar.</p>
        <label>Nombre de la referencia <input formControlName="referenciaNombre" maxlength="150" /></label>
        <label for="telefono-referencia">Teléfono de la referencia</label>
        <app-campo-telefono class="mb-3 block" idCampo="telefono-referencia" etiqueta="Teléfono de la referencia" formControlName="referenciaTelefono" />
      </fieldset>
      <fieldset class="grupo-campos">
        <legend>Su cuenta bancaria</legend>
        <p class="suave">Una cuenta a su nombre, distinta de los datos de la referencia.</p>
        <label>Banco o cooperativa <input formControlName="banco" maxlength="100" /></label>
        <label>Tipo de cuenta
          <select formControlName="tipoCuentaBanco">
            <option value="AHORROS">Ahorros</option>
            <option value="CORRIENTE">Corriente</option>
          </select>
        </label>
        <label>Número de cuenta <input formControlName="numeroCuentaBanco" inputmode="numeric" maxlength="40" /></label>
      </fieldset>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <div class="pie-formulario">
        <button type="submit" [disabled]="enviando()">{{ textoBoton() }}</button>
        @if (cancelable()) { <button type="button" class="secundario" (click)="cancelado.emit()">Cancelar</button> }
        @if (errores().length) {
          <ul class="errores-formulario" role="alert">@for (e of errores(); track e) { <li>{{ e }}</li> }</ul>
        }
      </div>
    </form>
  `,
})
export class FormDatosCredito implements OnInit {
  readonly perfil = input<Perfil | null>(null);
  readonly textoBoton = input('Guardar datos');
  readonly cancelable = input(false);
  /** Si se indica, tesorería guarda los datos de esa persona (trámite en ventanilla) en lugar de los propios. */
  readonly usuarioId = input<number | null>(null);
  readonly guardado = output<Perfil>();
  readonly cancelado = output<void>();

  private readonly api = inject(ApiService);

  protected readonly fechaMaxima = fechaMayorDeEdad();
  private static readonly ETIQUETAS: Record<string, string> = {
    direccion: 'La dirección', lugarTrabajo: 'El lugar de trabajo', ocupacion: 'La ocupación',
    antiguedadTrabajo: 'La antigüedad', ingresosMensuales: 'Los ingresos', fechaNacimiento: 'La fecha de nacimiento',
    estadoCivil: 'El estado civil', referenciaNombre: 'El nombre de la referencia', referenciaTelefono: 'El teléfono de la referencia',
    banco: 'El banco', tipoCuentaBanco: 'El tipo de cuenta', numeroCuentaBanco: 'El número de cuenta',
  };

  protected readonly form = inject(NonNullableFormBuilder).group({
    direccion: ['', conTexto],
    lugarTrabajo: ['', conTexto],
    ocupacion: ['', conTexto],
    antiguedadTrabajo: ['', conTexto],
    ingresosMensuales: [0, [Validators.required, Validators.min(0)]],
    fechaNacimiento: ['', [Validators.required, mayorDeEdad]],
    estadoCivil: ['SOLTERO', Validators.required],
    referenciaNombre: ['', conTexto],
    referenciaTelefono: ['', [Validators.required, telefonoValido]],
    banco: ['', conTexto],
    tipoCuentaBanco: ['AHORROS', Validators.required],
    numeroCuentaBanco: ['', [...conTexto, Validators.pattern(/^[0-9 -]+$/)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  private readonly intentado = signal(false);

  ngOnInit(): void {
    const p = this.perfil();
    if (p) {
      this.form.patchValue({
        direccion: p.direccion ?? '', lugarTrabajo: p.lugarTrabajo ?? '', ocupacion: p.ocupacion ?? '',
        antiguedadTrabajo: p.antiguedadTrabajo ?? '', ingresosMensuales: p.ingresosMensuales ?? 0,
        fechaNacimiento: p.fechaNacimiento ?? '', estadoCivil: p.estadoCivil ?? 'SOLTERO',
        referenciaNombre: p.referenciaNombre ?? '', referenciaTelefono: p.referenciaTelefono ?? '', banco: p.banco ?? '',
        tipoCuentaBanco: p.tipoCuentaBanco ?? 'AHORROS', numeroCuentaBanco: p.numeroCuentaBanco ?? '',
      });
      // Lo guardado antes de estas reglas puede no cumplirlas: se señala desde el principio.
      if (p.datosCreditoCompletos) {
        this.form.markAllAsTouched();
      }
    }
  }

  protected errores(): string[] {
    return erroresDeFormulario(this.form, FormDatosCredito.ETIQUETAS, this.intentado());
  }

  /** La dirección va con el contacto y lo demás con los datos para crédito: son dos rutas del perfil. */
  protected guardar(): void {
    this.intentado.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { direccion, ...credito } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    const otra = this.usuarioId();
    const accion = otra
      ? this.api.datosCreditoTramite(otra, direccion.trim(), credito as DatosCredito)
      : this.api.actualizarContacto({ telefono: this.perfil()?.telefono ?? '', direccion: direccion.trim() }).pipe(
        switchMap(() => this.api.actualizarDatosCredito(credito as DatosCredito)),
      );
    accion.subscribe({
      next: (p) => {
        this.enviando.set(false);
        this.guardado.emit(p);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
