import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, CreditoCartera, PersonaCartera } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { nombreFamilia } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';
import { coincide, Grupo, nombreModalidad, sumar } from './hojas';

interface FilaCredito {
  persona: PersonaCartera;
  credito: CreditoCartera;
}

interface TipoCredito {
  clave: string;
  titulo: string;
  letras: boolean;
  filas: FilaCredito[];
}

/** Orden de los tipos, como en la hoja de Excel: primero los emergentes y luego los créditos a letras. */
const ORDEN_TIPOS = ['EMERGENTE_SOCIO', 'EMERGENTE_ESTACIONAL', 'EMERGENTE_CLIENTE', 'LETRAS'];

/** Solo para socios: todas las personas con todos sus créditos activos y lo que deben. El backend también lo exige. */
@Component({
  selector: 'app-cartera',
  imports: [CurrencyPipe, FormsModule],
  template: `
    <h1>Cartera de créditos</h1>
    <p class="suave">Área exclusiva de socios: cada persona con todos sus créditos activos y lo que debe hoy.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (personas() === null) {
      <p class="cargando">Cargando la cartera…</p>
    } @else {
      <div class="total">
        <div>
          <p class="m-0 text-sm text-lateral-texto">Total que se debe a la caja</p>
          <p class="total-importe">{{ totales().totalDeuda | currency: 'USD' }}</p>
        </div>
        <p class="total-partes">
          <span>Capital prestado: <strong>{{ totales().saldoActual | currency: 'USD' }}</strong></span>
          <span>Intereses y mora por pagar: <strong>{{ totales().cargos | currency: 'USD' }}</strong></span>
          <span>{{ visibles().length }} personas · {{ totales().creditos }} créditos</span>
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
        <p class="vacio">No hay créditos activos{{ buscar() ? ' que coincidan con la búsqueda' : '' }}.</p>
      } @else {
        <section class="seccion">
          <h2 class="seccion-titulo">Deuda por persona</h2>
          <div class="tabla">
            <table class="tabla-compacta">
              <thead>
                <tr>
                  <th scope="col">Persona</th><th scope="col">Créditos</th><th scope="col" class="num">Saldo actual</th>
                  <th scope="col" class="num">Intereses y mora</th><th scope="col" class="num">Total deuda</th><th scope="col">Letras</th>
                </tr>
              </thead>
              <tbody>
                @for (p of visibles(); track p.id) {
                  <tr>
                    <td>{{ p.nombre }} @if (p.socio) { <span class="estado estado-informacion">Socio</span> }</td>
                    <td>{{ resumenCreditos(p) }}</td>
                    <td class="num">{{ p.saldoActual | currency: 'USD' }}</td>
                    <td class="num">{{ p.cargosPorPagar | currency: 'USD' }}</td>
                    <td class="num"><strong>{{ p.totalDeuda | currency: 'USD' }}</strong></td>
                    <td>@if (p.letrasAtrasadas > 0) { <span class="estado estado-peligro">{{ p.letrasAtrasadas }} atrasadas</span> } @else { Al día }</td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr>
                  <td>Total</td><td>{{ totales().creditos }}</td>
                  <td class="num">{{ totales().saldoActual | currency: 'USD' }}</td>
                  <td class="num">{{ totales().cargos | currency: 'USD' }}</td>
                  <td class="num">{{ totales().totalDeuda | currency: 'USD' }}</td>
                  <td></td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>

        @for (t of tipos(); track t.clave) {
          <section class="seccion">
            <h2 class="seccion-titulo">{{ t.titulo }}</h2>
            <div class="tabla">
              @if (t.letras) {
                <table class="tabla-compacta">
                  <thead>
                    <tr>
                      <th scope="col">Persona</th><th scope="col" class="num">Saldo inicial</th><th scope="col" class="num">Saldo actual</th>
                      <th scope="col">Pagadas</th><th scope="col" class="num">Capital</th><th scope="col" class="num">Interés</th>
                      <th scope="col" class="num">F.D.</th><th scope="col" class="num">Ahorro</th><th scope="col" class="num">Letra</th>
                      <th scope="col" class="num">Ahorro letras</th><th scope="col" class="num">Encaje</th><th scope="col" class="num">Mora</th>
                      <th scope="col" class="num">Total deuda</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (f of t.filas; track f.credito.id) {
                      <tr>
                        <td>{{ f.persona.nombre }} <span class="suave">{{ f.credito.codigo }}</span></td>
                        <td class="num">{{ f.credito.saldoInicial | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.saldoActual | currency: 'USD' }}</td>
                        <td>
                          {{ f.credito.letrasPagadas }} de {{ f.credito.letras }}
                          @if (f.credito.letrasAtrasadas > 0) { <span class="estado estado-peligro">{{ f.credito.letrasAtrasadas }} atrasadas</span> }
                        </td>
                        <td class="num">{{ f.credito.letraCapital | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.letraInteres | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.letraDesgravamen | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.letraAhorro | currency: 'USD' }}</td>
                        <td class="num"><strong>{{ f.credito.letra | currency: 'USD' }}</strong></td>
                        <td class="num">{{ f.credito.ahorroLetras | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.encaje | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.cargosPorPagar | currency: 'USD' }}</td>
                        <td class="num"><strong>{{ f.credito.totalDeuda | currency: 'USD' }}</strong></td>
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total</td>
                      <td class="num">{{ suma(t, 'saldoInicial') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'saldoActual') | currency: 'USD' }}</td>
                      <td></td>
                      <td class="num">{{ suma(t, 'letraCapital') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'letraInteres') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'letraDesgravamen') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'letraAhorro') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'letra') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'ahorroLetras') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'encaje') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'cargosPorPagar') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'totalDeuda') | currency: 'USD' }}</td>
                    </tr>
                  </tfoot>
                </table>
              } @else {
                <table class="tabla-compacta">
                  <thead>
                    <tr>
                      <th scope="col">Persona</th><th scope="col" class="num">Prestado</th><th scope="col" class="num">Saldo actual</th>
                      <th scope="col" class="num">Interés y F.D. por pagar</th><th scope="col" class="num">Total deuda</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (f of t.filas; track f.credito.id) {
                      <tr>
                        <td>{{ f.persona.nombre }} <span class="suave">{{ f.credito.codigo }}</span></td>
                        <td class="num">{{ f.credito.saldoInicial | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.saldoActual | currency: 'USD' }}</td>
                        <td class="num">{{ f.credito.cargosPorPagar | currency: 'USD' }}</td>
                        <td class="num"><strong>{{ f.credito.totalDeuda | currency: 'USD' }}</strong></td>
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <td>Total</td>
                      <td class="num">{{ suma(t, 'saldoInicial') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'saldoActual') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'cargosPorPagar') | currency: 'USD' }}</td>
                      <td class="num">{{ suma(t, 'totalDeuda') | currency: 'USD' }}</td>
                    </tr>
                  </tfoot>
                </table>
              }
            </div>
          </section>
        }
        <p class="suave">
          En los créditos a letras, el desglose es el de la próxima letra por pagar y el total de la deuda suma todas las letras que faltan,
          con su interés, fondo de desgravamen (F.D.), ahorro y mora. En los emergentes, la deuda es el saldo más el interés y el F.D. corridos hasta hoy.
        </p>
      }
    }
  `,
})
export class Cartera implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly grupos: { valor: Grupo; titulo: string }[] = [
    { valor: 'TODOS', titulo: 'Todos' },
    { valor: 'SOCIOS', titulo: 'Socios' },
    { valor: 'CLIENTES', titulo: 'Clientes' },
  ];
  protected readonly personas = signal<PersonaCartera[] | null>(null);
  protected readonly grupo = signal<Grupo>('TODOS');
  protected readonly buscar = signal('');
  protected readonly error = signal<string | null>(null);

  protected readonly visibles = computed(() => (this.personas() ?? []).filter((p) => coincide(p, this.grupo(), this.buscar())));

  protected readonly totales = computed(() => {
    const lista = this.visibles();
    return {
      creditos: lista.reduce((n, p) => n + p.creditos.length, 0),
      saldoActual: sumar(lista.map((p) => p.saldoActual)),
      cargos: sumar(lista.map((p) => p.cargosPorPagar)),
      totalDeuda: sumar(lista.map((p) => p.totalDeuda)),
    };
  });

  /** Una tabla por tipo de crédito, como los bloques de la hoja de Excel. */
  protected readonly tipos = computed(() => {
    const porTipo = new Map<string, TipoCredito>();
    for (const persona of this.visibles()) {
      for (const credito of persona.creditos) {
        const clave = `${credito.familia}|${credito.modalidad ?? ''}`;
        let tipo = porTipo.get(clave);
        if (!tipo) {
          const letras = credito.familia === 'LETRAS';
          const titulo = letras ? `${nombreFamilia(credito.familia)} · ${nombreModalidad(credito.modalidad)}` : nombreFamilia(credito.familia);
          tipo = { clave, titulo, letras, filas: [] };
          porTipo.set(clave, tipo);
        }
        tipo.filas.push({ persona, credito });
      }
    }
    const orden = (t: TipoCredito) => ORDEN_TIPOS.indexOf(t.clave.split('|')[0]);
    return [...porTipo.values()].sort((a, b) => orden(a) - orden(b) || a.titulo.localeCompare(b.titulo, 'es'));
  });

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.carteraGeneral().subscribe({ next: (d) => this.personas.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected suma(tipo: TipoCredito, campo: keyof CreditoCartera): number {
    return sumar(tipo.filas.map((f) => f.credito[campo] as number | null));
  }

  protected resumenCreditos(p: PersonaCartera): string {
    return p.creditos
      .map((c) => (c.familia === 'LETRAS' ? `Letras ${nombreModalidad(c.modalidad).toLowerCase()}` : nombreFamilia(c.familia)))
      .join(', ');
  }
}
