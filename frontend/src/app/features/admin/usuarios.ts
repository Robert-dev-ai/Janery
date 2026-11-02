import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Pagina, UsuarioResumen } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { DialogoService } from '../../shared/components/dialogo';
import { nombreCargo } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-usuarios',
  imports: [Estado, FormsModule, Paginador],
  template: `
    <h1>Usuarios</h1>
    <p class="suave">Lo que puede hacer cada cargo se define en Roles y permisos.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }
    @if (temporal(); as t) {
      <section class="tarjeta seccion-accion" role="status">
        <h2>Contraseña temporal de {{ t.para }}</h2>
        <p class="m-0 font-mono text-2xl font-bold tracking-wider select-all">{{ t.clave }}</p>
        <p class="suave mt-2">Entréguesela en persona o por mensaje. Solo se muestra esta vez: al entrar con ella, deberá elegir una nueva.</p>
        <button type="button" class="secundario" (click)="temporal.set(null)">Listo, ya la entregué</button>
      </section>
    }
    @if (pendientes().length) {
      <p class="alerta-atraso" role="alert">
        {{ pendientes().length === 1 ? 'Una persona pide' : pendientes().length + ' personas piden' }} restablecer su contraseña o tiene el acceso bloqueado:
        {{ nombresPendientes() }}.
      </p>
    }
    <div class="herramientas">
      <label>Buscar en esta página
        <input type="search" placeholder="Nombre, cédula, correo o cargo" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" />
      </label>
    </div>
    @if (datos(); as d) {
      <div class="tabla">
        <table>
          <thead><tr><th>Persona</th><th>Correo</th><th>Acceso</th><th>Socio</th><th>Cargos</th><th>Asignar cargo</th></tr></thead>
          <tbody>
            @for (u of filas(); track u.id) {
              <tr>
                <td>{{ u.nombre ?? 'Sin datos personales' }}@if (u.identificacion) { <br /><small class="suave">{{ u.identificacion }}</small> }</td>
                <td>
                  {{ u.correo }}<br />
                  <button type="button" class="enlace-boton" (click)="corregirCorreo(u)">Corregir correo</button>
                </td>
                <td>
                  <app-estado [valor]="u.estado" />
                  @if (u.pideRestablecer) { <br /><small class="negativo">Pidió restablecer</small> }
                  <br /><button type="button" class="enlace-boton" (click)="restablecer(u)">
                    {{ u.estado === 'BLOQUEADO' ? 'Desbloquear y dar contraseña' : 'Restablecer contraseña' }}
                  </button>
                </td>
                <td>
                  @if (u.socio) {
                    Sí
                  } @else {
                    <button type="button" class="secundario" (click)="hacerSocio(u)">Registrar socio</button>
                  }
                </td>
                <td class="acciones">
                  @for (c of u.cargos; track c) {
                    <button type="button" class="secundario" [attr.aria-label]="'Revocar ' + cargo(c)" (click)="revocar(u, c)">{{ cargo(c) }} ✕</button>
                  } @empty {
                    <span class="suave">Sin cargo</span>
                  }
                </td>
                <td>
                  <select #elegido (change)="asignar(u, elegido.value); elegido.value = ''" [attr.aria-label]="'Asignar cargo a ' + quien(u)">
                    <option value="">Elegir…</option>
                    @for (c of cargos; track c) {
                      @if (!u.cargos.includes(c)) { <option [value]="c">{{ cargo(c) }}</option> }
                    }
                  </select>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
    }
  `,
})
export class AdminUsuarios implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly cargo = nombreCargo;
  protected readonly cargos = ['ADMINISTRACION', 'PRESIDENCIA', 'VICEPRESIDENCIA', 'SECRETARIA', 'TESORERIA',
    'CAJA_CHICA', 'COMITE_CREDITO', 'ASUNTOS_SOCIALES'];
  protected readonly datos = signal<Pagina<UsuarioResumen> | null>(null);
  protected readonly busqueda = signal('');
  protected readonly filas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return (this.datos()?.contenido ?? []).filter((x) => !texto
      || [x.nombre, x.identificacion, x.correo, ...x.cargos.map(nombreCargo)].join(' ').toLowerCase().includes(texto));
  });
  protected readonly pendientes = computed(() => (this.datos()?.contenido ?? []).filter((u) => u.pideRestablecer || u.estado === 'BLOQUEADO'));
  protected readonly nombresPendientes = computed(() => this.pendientes().map((u) => this.quien(u)).join(', '));
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly temporal = signal<{ para: string; clave: string } | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected quien(u: UsuarioResumen): string {
    return u.nombre ?? u.correo;
  }

  protected cargar(pagina: number): void {
    this.api.usuarios(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async asignar(u: UsuarioResumen, cargo: string): Promise<void> {
    if (cargo && await this.dialogo.confirmar(`¿Asignar ${nombreCargo(cargo)} a ${this.quien(u)}?`, 'Asignar')) {
      this.ejecutar(this.api.asignarCargo(u.id, cargo));
    }
  }

  protected async hacerSocio(u: UsuarioResumen): Promise<void> {
    if (await this.dialogo.confirmar(`¿Registrar a ${this.quien(u)} como socio? Sus créditos nuevos usarán las condiciones de socio.`, 'Registrar socio')) {
      this.ejecutar(this.api.registrarSocio(u.id));
    }
  }

  protected async revocar(u: UsuarioResumen, cargo: string): Promise<void> {
    if (await this.dialogo.confirmar(`¿Quitar ${nombreCargo(cargo)} a ${this.quien(u)}?`, 'Quitar', true)) {
      this.ejecutar(this.api.revocarCargo(u.id, cargo));
    }
  }

  protected async restablecer(u: UsuarioResumen): Promise<void> {
    const mensaje = `¿Dar una contraseña temporal a ${this.quien(u)}? Su contraseña actual dejará de servir y se cerrarán sus sesiones.`;
    if (!(await this.dialogo.confirmar(mensaje, 'Dar contraseña temporal', true))) {
      return;
    }
    this.error.set(null);
    this.aviso.set(null);
    this.api.restablecerContrasena(u.id).subscribe({
      next: (r) => {
        this.temporal.set({ para: this.quien(u), clave: r.contrasenaTemporal });
        this.cargar(this.datos()?.pagina ?? 0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  /** Para quien se registró con el correo mal escrito: con el correo corregido ya puede entrar con él. */
  protected async corregirCorreo(u: UsuarioResumen): Promise<void> {
    const correo = await this.dialogo.pedirTexto(`Correo correcto de ${this.quien(u)} (ahora: ${u.correo}):`, 'Guardar correo');
    if (!correo?.trim()) {
      return;
    }
    this.aviso.set(null);
    this.ejecutar(this.api.cambiarCorreo(u.id, correo.trim()), `Correo de ${this.quien(u)} cambiado a ${correo.trim()}.`);
  }

  private ejecutar(accion: Observable<void>, aviso?: string): void {
    this.error.set(null);
    accion.subscribe({
      next: () => {
        if (aviso) {
          this.aviso.set(aviso);
        }
        this.cargar(this.datos()?.pagina ?? 0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
