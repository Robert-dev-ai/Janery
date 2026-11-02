import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth';
import { ApiService, DatosCredito, Perfil } from '../../core/services/api';
import { rolDe } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-perfil',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink],
  template: `
    @if (!auth.yo()?.tienePerfil) {
      <section class="tarjeta">
        <p><strong>Correo:</strong> {{ auth.yo()?.correo }}</p>
        <p><strong>Rol:</strong> {{ rol() }}</p>
        <p>Todavía no ha completado sus datos personales.</p>
        <a class="boton" routerLink="/activar-cuenta">Completar datos</a>
      </section>
    } @else if (perfil(); as p) {
      <dl class="tarjeta">
        <dt><strong>Correo</strong></dt><dd>{{ auth.yo()?.correo }}</dd>
        <dt><strong>Rol</strong></dt><dd>{{ rol() }}</dd>
        <dt><strong>Nombre</strong></dt><dd>{{ p.nombres }} {{ p.apellidos }}</dd>
        <dt><strong>Identificación</strong></dt><dd>{{ p.identificacion }}</dd>
        @if (!editando()) {
          <dt><strong>Teléfono</strong></dt><dd>{{ p.telefono }}</dd>
          <dt><strong>Dirección</strong></dt><dd>{{ p.direccion ?? 'Sin registrar' }}</dd>
        }
      </dl>
      @if (editando()) {
        <form class="tarjeta" [formGroup]="form" (ngSubmit)="guardar()">
          <h2>Editar datos de contacto</h2>
          <label>Teléfono <input type="tel" formControlName="telefono" autocomplete="tel" maxlength="20" /></label>
          <label>Dirección <input formControlName="direccion" autocomplete="street-address" maxlength="200" /></label>
          <div class="acciones">
            <button type="submit" [disabled]="form.invalid || enviando()">Guardar</button>
            <button type="button" class="secundario" (click)="editando.set(false)">Cancelar</button>
          </div>
        </form>
      } @else {
        <button type="button" (click)="editar(p)">Editar teléfono y dirección</button>
      }
      @if (guardado()) { <p class="exito mt-3" role="status">Datos actualizados.</p> }

      <section class="tarjeta mt-4">
        <h2>Datos para crédito</h2>
        @if (!p.datosCreditoCompletos) { <p class="suave">Complételos, junto con su dirección, para poder solicitar un crédito a letras.</p> }
        @if (editandoCredito()) {
          <form [formGroup]="credito" (ngSubmit)="guardarCredito()">
            <label>Lugar de trabajo <input formControlName="lugarTrabajo" maxlength="150" /></label>
            <label>Ocupación <input formControlName="ocupacion" maxlength="100" /></label>
            <label>Antigüedad en el trabajo <input formControlName="antiguedadTrabajo" maxlength="50" placeholder="Ejemplo: 2 años" /></label>
            <label>Ingresos mensuales (USD) <input type="number" min="0" step="1" formControlName="ingresosMensuales" /></label>
            <label>Fecha de nacimiento <input type="date" formControlName="fechaNacimiento" /></label>
            <label>Estado civil
              <select formControlName="estadoCivil">
                <option value="SOLTERO">Soltero/a</option>
                <option value="CASADO">Casado/a</option>
                <option value="UNION_LIBRE">Unión libre</option>
                <option value="DIVORCIADO">Divorciado/a</option>
                <option value="VIUDO">Viudo/a</option>
              </select>
            </label>
            <label>Nombre de su referencia <input formControlName="referenciaNombre" maxlength="150" /></label>
            <label>Teléfono de su referencia <input type="tel" formControlName="referenciaTelefono" maxlength="20" /></label>
            <label>Banco <input formControlName="banco" maxlength="100" /></label>
            <label>Tipo de cuenta
              <select formControlName="tipoCuentaBanco">
                <option value="AHORROS">Ahorros</option>
                <option value="CORRIENTE">Corriente</option>
              </select>
            </label>
            <label>Número de cuenta <input formControlName="numeroCuentaBanco" maxlength="40" /></label>
            <div class="acciones">
              <button type="submit" [disabled]="credito.invalid || enviando()">Guardar</button>
              <button type="button" class="secundario" (click)="editandoCredito.set(false)">Cancelar</button>
            </div>
          </form>
        } @else {
          <dl>
            <dt><strong>Lugar de trabajo</strong></dt><dd>{{ p.lugarTrabajo ?? '—' }}</dd>
            <dt><strong>Ocupación y antigüedad</strong></dt><dd>{{ p.ocupacion ?? '—' }} · {{ p.antiguedadTrabajo ?? '—' }}</dd>
            <dt><strong>Ingresos mensuales</strong></dt><dd>{{ p.ingresosMensuales == null ? '—' : (p.ingresosMensuales | currency: 'USD') }}</dd>
            <dt><strong>Fecha de nacimiento</strong></dt><dd>{{ p.fechaNacimiento ?? '—' }}</dd>
            <dt><strong>Referencia</strong></dt><dd>{{ p.referenciaNombre ?? '—' }} · {{ p.referenciaTelefono ?? '—' }}</dd>
            <dt><strong>Cuenta bancaria</strong></dt><dd>{{ p.banco ?? '—' }} {{ p.numeroCuentaBanco ?? '' }}</dd>
          </dl>
          <button type="button" class="secundario" (click)="editarCredito(p)">Editar datos para crédito</button>
        }
      </section>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
    @if (error()) { <p class="error mt-3" role="alert">{{ error() }}</p> }
  `,
})
export class PerfilPagina implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    telefono: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(20)]],
    direccion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
  });
  protected readonly credito = inject(NonNullableFormBuilder).group({
    lugarTrabajo: ['', [Validators.required, Validators.pattern(/\S/)]],
    ocupacion: ['', [Validators.required, Validators.pattern(/\S/)]],
    antiguedadTrabajo: ['', [Validators.required, Validators.pattern(/\S/)]],
    ingresosMensuales: [0, [Validators.required, Validators.min(0)]],
    fechaNacimiento: ['', Validators.required],
    estadoCivil: ['SOLTERO', Validators.required],
    referenciaNombre: ['', [Validators.required, Validators.pattern(/\S/)]],
    referenciaTelefono: ['', [Validators.required, Validators.pattern(/\S/)]],
    banco: ['', [Validators.required, Validators.pattern(/\S/)]],
    tipoCuentaBanco: ['AHORROS', Validators.required],
    numeroCuentaBanco: ['', [Validators.required, Validators.pattern(/\S/)]],
  });
  protected readonly rol = computed(() => rolDe(this.auth.yo()));
  protected readonly editandoCredito = signal(false);
  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly editando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly guardado = signal(false);

  ngOnInit(): void {
    if (this.auth.yo()?.tienePerfil) {
      this.api.miPerfil().subscribe({
        next: (p) => this.perfil.set(p),
        error: (e) => this.error.set(mensajeDeError(e)),
      });
    }
  }

  protected editar(p: Perfil): void {
    this.form.setValue({ telefono: p.telefono, direccion: p.direccion ?? '' });
    this.guardado.set(false);
    this.error.set(null);
    this.editando.set(true);
  }

  protected editarCredito(p: Perfil): void {
    this.credito.setValue({
      lugarTrabajo: p.lugarTrabajo ?? '', ocupacion: p.ocupacion ?? '', antiguedadTrabajo: p.antiguedadTrabajo ?? '',
      ingresosMensuales: p.ingresosMensuales ?? 0, fechaNacimiento: p.fechaNacimiento ?? '', estadoCivil: p.estadoCivil ?? 'SOLTERO',
      referenciaNombre: p.referenciaNombre ?? '', referenciaTelefono: p.referenciaTelefono ?? '', banco: p.banco ?? '',
      tipoCuentaBanco: p.tipoCuentaBanco ?? 'AHORROS', numeroCuentaBanco: p.numeroCuentaBanco ?? '',
    });
    this.guardado.set(false);
    this.error.set(null);
    this.editandoCredito.set(true);
  }

  protected guardarCredito(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.api.actualizarDatosCredito(this.credito.getRawValue() as DatosCredito).subscribe({
      next: (p) => {
        this.perfil.set(p);
        this.editandoCredito.set(false);
        this.guardado.set(true);
        this.enviando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }

  protected guardar(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.api.actualizarContacto(this.form.getRawValue()).subscribe({
      next: (p) => {
        this.perfil.set(p);
        this.editando.set(false);
        this.guardado.set(true);
        this.enviando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
