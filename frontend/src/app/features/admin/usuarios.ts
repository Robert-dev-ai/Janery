import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Pagina, UsuarioResumen } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-usuarios',
  imports: [Estado, FormsModule, Paginador],
  template: `
    <h1>Usuarios</h1>
    <p class="suave">Lo que puede hacer cada cargo se define en Permisos.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    <div class="herramientas">
      <label>Buscar en esta página
        <input type="search" placeholder="Correo o cargo" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" />
      </label>
    </div>
    @if (datos(); as d) {
      <div class="tabla">
        <table>
          <thead><tr><th>Correo</th><th>Estado</th><th>Socio</th><th>Cargos</th><th>Asignar</th></tr></thead>
          <tbody>
            @for (u of filas(); track u.id) {
              <tr>
                <td>{{ u.correo }}</td>
                <td><app-estado [valor]="u.estado" /></td>
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
      <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
    }
  `,
})
export class AdminUsuarios implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly cargos = ['ADMINISTRACION', 'PRESIDENCIA', 'VICEPRESIDENCIA', 'SECRETARIA', 'TESORERIA',
    'CAJA_CHICA', 'COMITE_CREDITO', 'ASUNTOS_SOCIALES'];
  protected readonly datos = signal<Pagina<UsuarioResumen> | null>(null);
  protected readonly busqueda = signal('');
  protected readonly filas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return (this.datos()?.contenido ?? []).filter((x) => !texto || (x.correo + ' ' + x.cargos.join(' ')).toLowerCase().includes(texto));
  });
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

  protected async asignar(u: UsuarioResumen, cargo: string): Promise<void> {
    if (cargo && await this.dialogo.confirmar(`¿Asignar ${cargo} a ${u.correo}?`, 'Asignar')) {
      this.ejecutar(this.api.asignarCargo(u.id, cargo));
    }
  }

  protected async hacerSocio(u: UsuarioResumen): Promise<void> {
    if (await this.dialogo.confirmar(`¿Registrar a ${u.correo} como socio? Sus créditos nuevos usarán las condiciones de socio.`, 'Registrar socio')) {
      this.ejecutar(this.api.registrarSocio(u.id));
    }
  }

  protected async revocar(u: UsuarioResumen, cargo: string): Promise<void> {
    if (await this.dialogo.confirmar(`¿Revocar ${cargo} a ${u.correo}?`, 'Revocar', true)) {
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
