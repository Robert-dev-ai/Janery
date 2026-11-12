import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';

import { ApiService, DuenoDinero, EstadoCajaRespuesta } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { GraficoCircular, PorcionGrafico } from '../../shared/components/grafico-circular';
import { GraficoColumnas, GrupoColumnas } from '../../shared/components/grafico-columnas';
import { mensajeDeError } from '../../shared/utils/errores';
import { nombreConcepto } from '../../shared/utils/etiquetas';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const CAJAS: Record<string, string> = { PRINCIPAL: 'Caja principal', CHICA: 'Caja chica', FONDO_RESERVA: 'Fondo de reserva' };
const MES_CORTO = new Intl.DateTimeFormat('es-EC', { month: 'short', timeZone: 'UTC' });
const MES_LARGO = new Intl.DateTimeFormat('es-EC', { month: 'long', year: 'numeric', timeZone: 'UTC' });
const fechaDe = (periodo: string) => new Date(`${periodo}-01T00:00:00Z`);

/** Los conceptos propios de la caja no tienen nombre en el catálogo: se leen a partir de su código. */
function etiquetaConcepto(codigo: string): string {
  const nombre = nombreConcepto(codigo);
  if (nombre !== codigo) {
    return nombre;
  }
  const texto = codigo.replaceAll('_', ' ').toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

@Component({
  selector: 'app-estado-caja',
  imports: [CurrencyPipe, GraficoCircular, GraficoColumnas],
  template: `
    <h1>Estado de caja</h1>
    <p class="suave">Saldos de hoy y, de la caja principal, lo ocurrido en los últimos doce meses.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <section class="seccion">
        <h2 class="seccion-titulo">Estado actual</h2>
        <div class="rejilla-dos">
          <div class="tarjeta">
            <h3>Dónde está el dinero</h3>
            <app-grafico-circular [datos]="saldos()" [textoTotal]="totalSaldos()" />
          </div>
          <div class="tarjeta">
            <h3>Resultado de los últimos doce meses</h3>
            <p class="importe" [class.positivo]="neto() > 0" [class.negativo]="neto() < 0">{{ neto() | currency: 'USD' }}</p>
            <p class="suave">Ganancias menos pérdidas registradas.</p>
            <dl class="ficha-datos">
              <dt>Ganancias en espera de reparto</dt><dd>{{ d.enEspera.ganancia | currency: 'USD' }}</dd>
              <dt>Pérdidas en espera de reparto</dt><dd>{{ d.enEspera.perdida | currency: 'USD' }}</dd>
            </dl>
            <p class="suave">Son centavos que aún no se pueden dividir entre todos los socios.</p>
          </div>
        </div>
      </section>

      <section class="seccion">
        <h2 class="seccion-titulo">A quién pertenece el dinero</h2>
        <p class="suave">Todo está en la caja y se puede usar para créditos, pero lo de cada persona se le debe. Lo que supera la caja está prestado.</p>
        <div class="rejilla-dos">
          <div class="tarjeta">
            <h3>Por tipo de fondo</h3>
            <app-grafico-circular [datos]="fondos()" [textoTotal]="totalDe(fondos())" />
          </div>
          <div class="tarjeta">
            <h3>Caja principal</h3>
            <dl class="ficha-datos">
              <dt>Saldo de la caja</dt><dd>{{ saldoPrincipal() | currency: 'USD' }}</dd>
              <dt>Dinero de las personas</dt><dd>{{ totalPersonas() | currency: 'USD' }}</dd>
              <dt>{{ saldoPrincipal() >= totalPersonas() ? 'Dinero de Janery' : 'Prestado de ese dinero' }}</dt><dd>{{ abs(saldoPrincipal() - totalPersonas()) | currency: 'USD' }}</dd>
            </dl>
          </div>
        </div>
        @if (d.caja.duenos.length) {
          <div class="tabla mt-4">
            <table>
              <thead><tr><th scope="col">Persona</th><th scope="col" class="num">Apertura</th><th scope="col" class="num">Cuenta</th><th scope="col" class="num">Ahorro normal</th><th scope="col" class="num">Plazo fijo</th><th scope="col" class="num">Encajes</th><th scope="col" class="num">Ahorro letras</th></tr></thead>
              <tbody>
                @for (x of d.caja.duenos; track x.nombre) {
                  <tr>
                    <td>{{ x.nombre }}</td>
                    <td class="num">{{ x.apertura | currency: 'USD' }}</td>
                    <td class="num">{{ x.cuenta | currency: 'USD' }}</td>
                    <td class="num">{{ x.ahorrosNormales | currency: 'USD' }}</td>
                    <td class="num">{{ x.plazosFijos | currency: 'USD' }}</td>
                    <td class="num">{{ x.encajes | currency: 'USD' }}</td>
                    <td class="num">{{ x.ahorroLetras | currency: 'USD' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <section class="seccion">
        <h2 class="seccion-titulo">Entradas y salidas por mes</h2>
        <div class="tarjeta">
          <app-grafico-columnas [series]="['Entradas', 'Salidas']" [grupos]="flujo()" />
        </div>
      </section>

      <section class="seccion">
        <h2 class="seccion-titulo">Ganancias y pérdidas por mes</h2>
        <div class="tarjeta">
          <app-grafico-columnas [series]="['Ganancias', 'Pérdidas']" [grupos]="resultados()" />
        </div>
      </section>

      <section class="seccion">
        <h2 class="seccion-titulo">De dónde viene y a dónde va</h2>
        <div class="rejilla-dos">
          <div class="tarjeta">
            <h3>Entradas por concepto</h3>
            <app-grafico-circular [datos]="entradas()" [textoTotal]="totalDe(entradas())" />
          </div>
          <div class="tarjeta">
            <h3>Salidas por concepto</h3>
            <app-grafico-circular [datos]="salidas()" [textoTotal]="totalDe(salidas())" />
          </div>
        </div>
      </section>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class EstadoCaja implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<EstadoCajaRespuesta | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly saldos = computed<PorcionGrafico[]>(() => (this.datos()?.caja.saldos ?? []).map((s) => ({
    etiqueta: CAJAS[s.codigo] ?? s.codigo, valor: s.saldo, texto: USD.format(s.saldo),
  })));
  protected readonly totalSaldos = computed(() => USD.format((this.datos()?.caja.saldos ?? []).reduce((t, s) => t + s.saldo, 0)));

  protected readonly flujo = computed<GrupoColumnas[]>(() => (this.datos()?.caja.meses ?? []).map((m) => ({
    etiqueta: MES_CORTO.format(fechaDe(m.periodo)), titulo: MES_LARGO.format(fechaDe(m.periodo)), valores: [m.entradas, m.salidas],
  })));
  protected readonly resultados = computed<GrupoColumnas[]>(() => (this.datos()?.caja.resultados ?? []).map((m) => ({
    etiqueta: MES_CORTO.format(fechaDe(m.periodo)), titulo: MES_LARGO.format(fechaDe(m.periodo)), valores: [m.ganancias, m.perdidas],
  })));
  protected readonly neto = computed(() => (this.datos()?.caja.resultados ?? []).reduce((t, m) => t + m.ganancias - m.perdidas, 0));

  protected readonly fondos = computed<PorcionGrafico[]>(() => {
    const duenos = this.datos()?.caja.duenos ?? [];
    const suma = (campo: (x: DuenoDinero) => number) => duenos.reduce((t, x) => t + campo(x), 0);
    return ([
      ['Cuentas principales', suma((x) => x.cuenta)],
      ['Ahorro normal', suma((x) => x.ahorrosNormales)],
      ['Plazo fijo', suma((x) => x.plazosFijos)],
      ['Encajes', suma((x) => x.encajes)],
      ['Ahorro letras', suma((x) => x.ahorroLetras)],
      ['Depósitos de apertura', suma((x) => x.apertura)],
    ] as [string, number][]).map(([etiqueta, valor]) => ({ etiqueta, valor, texto: USD.format(valor) }));
  });
  protected readonly totalPersonas = computed(() => this.fondos().reduce((t, f) => t + f.valor, 0));
  protected readonly saldoPrincipal = computed(() => this.datos()?.caja.saldos.find((s) => s.codigo === 'PRINCIPAL')?.saldo ?? 0);
  protected readonly abs = Math.abs;

  protected readonly entradas = computed(() => this.porConcepto(this.datos()?.caja.entradas));
  protected readonly salidas = computed(() => this.porConcepto(this.datos()?.caja.salidas));

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.estadoCaja().subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected totalDe(porciones: PorcionGrafico[]): string {
    return USD.format(porciones.reduce((t, p) => t + p.valor, 0));
  }

  private porConcepto(lista: { concepto: string; importe: number }[] = []): PorcionGrafico[] {
    return lista.map((c) => ({ etiqueta: etiquetaConcepto(c.concepto), valor: c.importe, texto: USD.format(c.importe) }));
  }
}
