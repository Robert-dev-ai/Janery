import { Component, inject, OnInit, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService, Pagina, UsuarioResumen } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-usuarios',
  template: `
    <h1>Usuarios</h1>
    <p class="suave">Lo que puede hacer cada cargo se define en Permisos.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <div class="tabla">
        <table>
          <thead><tr><th>Correo</th><th>Estado</th><th>Socio</th><th>Cargos</th><th>Asignar</th></tr></thead>
          <tbody>
            @for (u of d.contenido; track u.id) {
              <tr>
                <td>{{ u.correo }}</td>
                <td>{{ u.estado }}</td>
                <td>
                  @if (u.socio) {
                    Sí
                  } @else {
                    <button type="button" class="secundario" (click)="hacerSocio(u)">Registrar socio</button>
                  }
                </td>
                <td class="acciones">
                  @for (c of u.cargos; track c) {
                    <button type="button" class="secundario" [attr.aria-label]="'Revocar ' + c" (click)="revocar(u, c)">{{ c }} ✕</button>
                  } @empty {
                    <span class="suave">Sin cargo</span>
                  }
                </td>
                <td>
                  <select #cargo (change)="asignar(u, cargo.value); cargo.value = ''" [attr.aria-label]="'Asignar cargo a ' + u.correo">
                    <option value="">Elegir…</option>
                    @for (c of cargos; track c) {
                      @if (!u.cargos.includes(c)) { <option [value]="c">{{ c }}</option> }
                    }
                  </select>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="paginador">
        <button type="button" class="secundario" [disabled]="d.pagina === 0" (click)="cargar(d.pagina - 1)">Anterior</button>
        <span>Página {{ d.pagina + 1 }}</span>
        <button type="button" class="secundario" [disabled]="(d.pagina + 1) * d.tamano >= d.total" (click)="cargar(d.pagina + 1)">Siguiente</button>
      </div>
    }
  `,
})
export class AdminUsuarios implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly cargos = ['ADMINISTRACION', 'PRESIDENCIA', 'VICEPRESIDENCIA', 'SECRETARIA', 'TESORERIA',
    'CAJA_CHICA', 'COMITE_CREDITO', 'ASUNTOS_SOCIALES'];
  protected readonly datos = signal<Pagina<UsuarioResumen> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.usuarios(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected asignar(u: UsuarioResumen, cargo: string): void {
    if (cargo && confirm(`¿Asignar ${cargo} a ${u.correo}?`)) {
      this.ejecutar(this.api.asignarCargo(u.id, cargo));
    }
  }

  protected hacerSocio(u: UsuarioResumen): void {
    if (confirm(`¿Registrar a ${u.correo} como socio? Sus créditos nuevos usarán las condiciones de socio.`)) {
      this.ejecutar(this.api.registrarSocio(u.id));
    }
  }

  protected revocar(u: UsuarioResumen, cargo: string): void {
    if (confirm(`¿Revocar ${cargo} a ${u.correo}?`)) {
      this.ejecutar(this.api.revocarCargo(u.id, cargo));
    }
  }

  private ejecutar(accion: Observable<void>): void {
    this.error.set(null);
    accion.subscribe({
      next: () => this.cargar(this.datos()?.pagina ?? 0),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
