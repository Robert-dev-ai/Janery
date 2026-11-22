import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, PersonaAhorros } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';
import { coincide, Grupo, sumar } from './hojas';

/** Solo para socios: todas las personas con sus ahorros y, los socios, con sus aportes. El backend también lo exige. */
@Component({
  selector: 'app-ahorros-aportes',
  imports: [CurrencyPipe, FormsModule],
  template: `
    <h1>Ahorros y aportes</h1>
    <p class="suave">Área exclusiva de socios: lo que cada persona tiene ahorrado en la caja y lo que cada socio ha aportado.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (personas() === null) {
      <p class="cargando">Cargando los ahorros…</p>
    } @else {
      <div class="total">
        <div>
          <p class="m-0 text-sm text-lateral-texto">Total de ahorros</p>
          <p class="total-importe">{{ total('totalAhorros') | currency: 'USD' }}</p>
        </div>
        <p class="total-partes">
          <span>Fondo de los socios: <strong>{{ total('fondoPersonal', conAportes()) | currency: 'USD' }}</strong></span>
          <span>Saldos en cuentas: <strong>{{ total('cuenta') | currency: 'USD' }}</strong></span>
          <span>Caja chica aportada: <strong>{{ total('cajaChica', conAportes()) | currency: 'USD' }}</strong></span>
        </p>
      </div>

      <div class="herramientas">
        <label>Buscar por nombre
          <input type="search" [ngModel]="buscar()" (ngModelChange)="buscar.set($event)" />
        </label>
      </div>
      <div class="pestanas" role="tablist">
        @for (g of grupos; track g.valor) {
          <button type="button" role="tab" [class.activo]="grupo() === g.valor" [attr.aria-selected]="grupo() === g.valor" (click)="grupo.set(g.valor)">{{ g.titulo }}</button>
        }
      </div>

      @if (visibles().length === 0) {
        <p class="vacio">No se encontraron personas.</p>
      } @else {
        <section class="seccion">
          <h2 class="seccion-titulo">Ahorros</h2>
          <div class="tabla">
            <table class="tabla-compacta">
              <thead>
                <tr>
                  <th scope="col">Persona</th><th scope="col" class="num">A la vista</th><th scope="col" class="num">Plazo fijo</th>
                  <th scope="col" class="num">Ahorro letras</th><th scope="col" class="num">Encaje</th><th scope="col" class="num">Total ahorros</th>
                  <th scope="col" class="num">Saldo en cuenta</th><th scope="col" class="num">Apertura</th>
                </tr>
              </thead>
              <tbody>
                @for (p of visibles(); track p.id) {
                  <tr>
                    <td>{{ p.nombre }} @if (p.socio) { <span class="estado estado-informacion">Socio</span> }</td>
                    <td class="num">{{ p.ahorrosNormales | currency: 'USD' }}</td>
                    <td class="num">{{ p.plazosFijos | currency: 'USD' }}</td>
                    <td class="num">{{ p.ahorroLetras | currency: 'USD' }}</td>
                    <td class="num">{{ p.encajes | currency: 'USD' }}</td>
                    <td class="num"><strong>{{ p.totalAhorros | currency: 'USD' }}</strong></td>
                    <td class="num">{{ p.cuenta | currency: 'USD' }}</td>
                    <td class="num">{{ p.apertura | currency: 'USD' }}</td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr>
                  <td>Total</td>
                  <td class="num">{{ total('ahorrosNormales') | currency: 'USD' }}</td>
                  <td class="num">{{ total('plazosFijos') | currency: 'USD' }}</td>
                  <td class="num">{{ total('ahorroLetras') | currency: 'USD' }}</td>
                  <td class="num">{{ total('encajes') | currency: 'USD' }}</td>
                  <td class="num">{{ total('totalAhorros') | currency: 'USD' }}</td>
                  <td class="num">{{ total('cuenta') | currency: 'USD' }}</td>
                  <td class="num">{{ total('apertura') | currency: 'USD' }}</td>
                </tr>
              </tfoot>
            </table>
          </div>
          <p class="suave">
            Total ahorros suma el ahorro a la vista, el plazo fijo, el ahorro de letras de sus créditos y el encaje que aún no se le devuelve.
            El saldo en cuenta y el depósito de apertura van aparte.
          </p>
        </section>

        @if (conAportes().length > 0) {
          <section class="seccion">
            <h2 class="seccion-titulo">Aportes de socios</h2>
            <div class="tabla">
              <table class="tabla-compacta">
                <thead>
                  <tr>
                    <th scope="col">Socio</th><th scope="col" class="num">Aporte inicial</th><th scope="col" class="num">Aportes mensuales</th>
                    <th scope="col" class="num">Meses</th><th scope="col" class="num">Aportes personales</th><th scope="col" class="num">Compraventas</th>
                    <th scope="col" class="num">Ganancias netas</th><th scope="col" class="num">Fondo personal</th><th scope="col" class="num">Caja chica</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of conAportes(); track p.id) {
                    <tr>
                      <td>{{ p.nombre }}</td>
                      <td class="num">{{ p.aporteInicial | currency: 'USD' }}</td>
                      <td class="num">{{ p.aportesMensuales | currency: 'USD' }}</td>
                      <td class="num">{{ p.mesesAportados }}</td>
                      <td class="num">{{ p.aportesPersonales | currency: 'USD' }}</td>
                      <td class="num">{{ p.compraventas | currency: 'USD' }}</td>
                      <td class="num">{{ p.ganancias | currency: 'USD' }}</td>
                      <td class="num"><strong>{{ p.fondoPersonal | currency: 'USD' }}</strong></td>
                      <td class="num">{{ p.cajaChica | currency: 'USD' }}</td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr>
                    <td>Total</td>
                    <td class="num">{{ total('aporteInicial', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('aportesMensuales', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('mesesAportados', conAportes()) }}</td>
                    <td class="num">{{ total('aportesPersonales', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('compraventas', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('ganancias', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('fondoPersonal', conAportes()) | currency: 'USD' }}</td>
                    <td class="num">{{ total('cajaChica', conAportes()) | currency: 'USD' }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p class="suave">El fondo personal suma los aportes, las compraventas y las ganancias netas de cada socio. La caja chica va aparte.</p>
          </section>
        }
      }
    }
  `,
})
export class AhorrosAportes implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly grupos: { valor: Grupo; titulo: string }[] = [
    { valor: 'TODOS', titulo: 'Todos' },
    { valor: 'SOCIOS', titulo: 'Socios' },
    { valor: 'CLIENTES', titulo: 'Clientes' },
  ];
  protected readonly personas = signal<PersonaAhorros[] | null>(null);
  protected readonly grupo = signal<Grupo>('TODOS');
  protected readonly buscar = signal('');
  protected readonly error = signal<string | null>(null);

  protected readonly visibles = computed(() => (this.personas() ?? []).filter((p) => coincide(p, this.grupo(), this.buscar())));

  /** Los socios activos y quien dejó de serlo pero aún tiene fondo o aportes de caja chica. */
  protected readonly conAportes = computed(() => this.visibles().filter((p) => p.socio || p.fondoPersonal !== 0 || p.cajaChica !== 0));

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.ahorrosGeneral().subscribe({ next: (d) => this.personas.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected total(campo: keyof PersonaAhorros, lista: PersonaAhorros[] = this.visibles()): number {
    return sumar(lista.map((p) => p[campo] as number));
  }
}
