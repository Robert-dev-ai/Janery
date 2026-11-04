import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CircleUser, LucideAngularModule } from 'lucide-angular';
import { of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Perfil } from '../../core/services/api';
import { FotoPerfilService } from '../../core/services/foto-perfil';
import { TemaService } from '../../core/services/tema';
import { CampoTelefono } from '../../shared/components/campo-telefono';
import { rolDe } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';
import { erroresDeFormulario, telefonoValido } from '../../shared/utils/validadores';
import { FormDatosCredito } from './form-datos-credito';

@Component({
  selector: 'app-perfil',
  imports: [CampoTelefono, CurrencyPipe, FormDatosCredito, LucideAngularModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="tarjeta">
      <div class="flex flex-wrap items-center gap-4">
        @if (foto.url(); as url) {
          <img class="avatar-grande" [src]="url" alt="Su foto de perfil" />
        } @else {
          <lucide-icon class="text-suave" [img]="iconos.CircleUser" [size]="96" aria-hidden="true" />
        }
        <div>
          <p class="m-0 text-lg font-semibold">{{ nombreCompleto() }}</p>
          <p class="suave m-0">{{ auth.yo()?.correo }} · {{ rol() }}</p>
          <div class="acciones mt-3">
            <label class="boton-claro m-0 cursor-pointer">{{ auth.yo()?.tieneFoto ? 'Cambiar foto' : 'Poner foto' }}
              <input class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" [disabled]="subiendoFoto()" (change)="subirFoto($event)" />
            </label>
            @if (auth.yo()?.tieneFoto) { <button type="button" class="secundario" (click)="quitarFoto()">Quitar foto</button> }
          </div>
          <p class="suave mt-2 mb-0">JPG, PNG o WEBP, hasta 2 MB.</p>
        </div>
      </div>
    </section>

    <section class="tarjeta">
      <h2>Apariencia</h2>
      <label class="fila-opcion">
        <input type="checkbox" [checked]="tema.oscuro()" (change)="tema.alternar()" />
        Modo oscuro
      </label>
      <p class="suave">Se recuerda en este dispositivo.</p>
    </section>

    @if (!auth.yo()?.tienePerfil) {
      <section class="tarjeta">
        <p>Todavía no ha completado sus datos personales.</p>
        <a class="boton" routerLink="/activar-cuenta">Completar datos</a>
      </section>
    } @else if (perfil(); as p) {
      <section class="tarjeta">
        <h2>Datos personales y de contacto</h2>
        <dl class="ficha-datos">
          <div><dt>Nombre</dt><dd>{{ p.nombres }} {{ p.apellidos }}</dd></div>
          <div><dt>Cédula</dt><dd>{{ p.identificacion }}</dd></div>
          @if (!editando()) {
            <div><dt>Correo</dt><dd>{{ auth.yo()?.correo }}</dd></div>
            <div><dt>Teléfono</dt><dd>{{ p.telefono }}</dd></div>
            <div><dt>Dirección</dt><dd>{{ p.direccion ?? 'Sin registrar' }}</dd></div>
          }
        </dl>
        @if (editando()) {
          <form [formGroup]="form" (ngSubmit)="guardar()">
            <label>Correo <input type="email" formControlName="correo" autocomplete="email" maxlength="254" /></label>
            @if (correoCambia()) {
              <label>Su contraseña actual, para confirmar el cambio de correo
                <input type="password" formControlName="contrasena" autocomplete="current-password" maxlength="72" />
              </label>
            }
            <label for="telefono-perfil">Teléfono</label>
            <app-campo-telefono class="mb-3 block" idCampo="telefono-perfil" formControlName="telefono" />
            <label>Dirección <input formControlName="direccion" autocomplete="street-address" maxlength="200" /></label>
            <div class="pie-formulario">
              <button type="submit" [disabled]="enviando()">Guardar</button>
              <button type="button" class="secundario" (click)="editando.set(false)">Cancelar</button>
              @if (errores().length) {
                <ul class="errores-formulario" role="alert">@for (e of errores(); track e) { <li>{{ e }}</li> }</ul>
              }
            </div>
          </form>
        } @else {
          <button type="button" (click)="editar(p)">Editar mis datos</button>
        }
        <p class="suave mt-3 mb-0">El nombre y la cédula no se pueden cambiar: a ellos está vinculada toda su información.</p>
      </section>
      @if (guardado()) { <p class="exito" role="status">Datos actualizados.</p> }

      <section class="tarjeta mt-4">
        <h2>Datos para crédito</h2>
        @if (!p.datosCreditoCompletos) { <p class="suave">Complételos para poder solicitar un crédito a letras.</p> }
        @if (editandoCredito()) {
          <app-form-datos-credito [perfil]="p" [cancelable]="true" (guardado)="creditoGuardado($event)" (cancelado)="editandoCredito.set(false)" />
        } @else {
          <dl class="ficha-datos">
            <div><dt>Lugar de trabajo</dt><dd>{{ p.lugarTrabajo ?? '—' }}</dd></div>
            <div><dt>Ocupación y antigüedad</dt><dd>{{ p.ocupacion ?? '—' }} · {{ p.antiguedadTrabajo ?? '—' }}</dd></div>
            <div><dt>Ingresos mensuales</dt><dd>{{ p.ingresosMensuales == null ? '—' : (p.ingresosMensuales | currency: 'USD') }}</dd></div>
            <div><dt>Fecha de nacimiento</dt><dd>{{ p.fechaNacimiento ?? '—' }}</dd></div>
            <div><dt>Referencia personal</dt><dd>{{ p.referenciaNombre ?? '—' }} · {{ p.referenciaTelefono ?? '—' }}</dd></div>
            <div><dt>Su cuenta bancaria</dt><dd>{{ p.banco ?? '—' }} · {{ p.tipoCuentaBanco ?? '' }} {{ p.numeroCuentaBanco ?? '' }}</dd></div>
          </dl>
          <button type="button" class="secundario" (click)="editarCredito()">Editar datos para crédito</button>
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
  protected readonly tema = inject(TemaService);
  protected readonly foto = inject(FotoPerfilService);
  private readonly api = inject(ApiService);

  protected readonly iconos = { CircleUser };
  private static readonly ETIQUETAS: Record<string, string> = {
    correo: 'El correo', contrasena: 'La contraseña actual',
    telefono: 'El teléfono', direccion: 'La dirección',
  };

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    // Solo se exige si cambia el correo; ver correoCambia().
    contrasena: [''],
    telefono: ['', [Validators.required, telefonoValido]],
    direccion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
  });
  protected readonly rol = computed(() => rolDe(this.auth.yo()));
  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly nombreCompleto = computed(() => {
    const p = this.perfil();
    return p ? `${p.nombres} ${p.apellidos}` : (this.auth.yo()?.nombre ?? '');
  });
  protected readonly editandoCredito = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly guardado = signal(false);
  protected readonly subiendoFoto = signal(false);
  private readonly intentado = signal(false);

  ngOnInit(): void {
    if (this.auth.yo()?.tienePerfil) {
      this.api.miPerfil().subscribe({
        next: (p) => this.perfil.set(p),
        error: (e) => this.error.set(mensajeDeError(e)),
      });
    }
  }

  protected errores(): string[] {
    return erroresDeFormulario(this.form, PerfilPagina.ETIQUETAS, this.intentado());
  }

  protected subirFoto(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    if (!archivo) {
      return;
    }
    this.subiendoFoto.set(true);
    this.error.set(null);
    this.auth.guardarFoto(archivo).subscribe({
      next: () => {
        this.subiendoFoto.set(false);
        campo.value = '';
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.subiendoFoto.set(false);
        campo.value = '';
      },
    });
  }

  protected quitarFoto(): void {
    this.auth.quitarFoto().subscribe({ error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected correoCambia(): boolean {
    return this.form.controls.correo.value.trim().toLowerCase() !== (this.auth.yo()?.correo ?? '');
  }

  protected editar(p: Perfil): void {
    this.form.setValue({
      correo: this.auth.yo()?.correo ?? '',
      contrasena: '',
      telefono: p.telefono,
      direccion: p.direccion ?? '',
    });
    this.form.markAllAsTouched();
    this.guardado.set(false);
    this.error.set(null);
    this.editando.set(true);
  }

  protected editarCredito(): void {
    this.guardado.set(false);
    this.error.set(null);
    this.editandoCredito.set(true);
  }

  protected creditoGuardado(p: Perfil): void {
    this.perfil.set(p);
    this.editandoCredito.set(false);
    this.guardado.set(true);
  }

  protected guardar(): void {
    this.intentado.set(true);
    const contrasena = this.form.controls.contrasena;
    contrasena.setErrors(this.correoCambia() && !contrasena.value ? { required: true } : null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { correo, telefono, direccion } = this.form.getRawValue();
    // Primero el correo: si la contraseña está mal, no se guarda nada.
    const cambioCorreo = this.correoCambia()
      ? this.auth.cambiarCorreo(correo.trim(), contrasena.value)
      : of(undefined);
    cambioCorreo.pipe(
      switchMap(() => this.api.actualizarContacto({ telefono, direccion: direccion.trim() })),
    ).subscribe({
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
