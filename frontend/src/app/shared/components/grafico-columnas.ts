import { Component, computed, input } from '@angular/core';

export interface GrupoColumnas {
  /** Texto corto del eje, por ejemplo "nov". */
  etiqueta: string;
  /** Texto completo del grupo, para el detalle, por ejemplo "noviembre 2026". */
  titulo: string;
  /** Un valor por serie, en el mismo orden que las series. */
  valores: number[];
}

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const COMPACTO = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });

/** El tope del eje: lo bastante alto para el valor mayor y divisible en cuatro marcas limpias. */
function topeRedondo(maximo: number): number {
  if (maximo <= 0) {
    return 1;
  }
  const magnitud = 10 ** Math.floor(Math.log10(maximo));
  const paso = [1, 2, 4, 8, 10].find((p) => p * magnitud >= maximo) ?? 10;
  return paso * magnitud;
}

/** Columnas por periodo, con una o más series sobre un solo eje. El detalle sale al pasar el puntero o al enfocar. */
@Component({
  selector: 'app-grafico-columnas',
  template: `
    @if (series().length > 1) {
      <ul class="leyenda leyenda-fila">
        @for (s of series(); track s; let i = $index) {
          <li><i [style.background]="color(i)"></i><span>{{ s }}</span></li>
        }
      </ul>
    }
    <div class="columnas">
      <div class="columnas-eje" aria-hidden="true">
        @for (t of marcas(); track t.valor) {
          <span [style.bottom.%]="t.altura">{{ t.texto }}</span>
        }
      </div>
      <div class="columnas-area">
        @for (t of marcas(); track t.valor) {
          <i class="columnas-guia" [style.bottom.%]="t.altura"></i>
        }
        @for (g of filas(); track g.titulo; let n = $index) {
          <div class="columnas-grupo" tabindex="0" [attr.aria-label]="g.resumen">
            <div class="columnas-barras">
              @for (b of g.barras; track b.serie) {
                <span class="columnas-barra" [style.height.%]="b.altura" [style.background]="b.color"></span>
              }
            </div>
            <span class="columnas-etiqueta">{{ g.etiqueta }}</span>
            <div class="columnas-detalle" [class.al-final]="n > filas().length / 2" role="presentation">
              <strong>{{ g.titulo }}</strong>
              @for (b of g.barras; track b.serie) {
                <span><i [style.background]="b.color"></i>{{ b.serie }}<b>{{ b.texto }}</b></span>
              }
            </div>
          </div>
        }
      </div>
    </div>
    <details class="grafico-tabla">
      <summary>Ver como tabla</summary>
      <div class="tabla">
        <table>
          <thead><tr><th>Periodo</th>@for (s of series(); track s) { <th class="num">{{ s }}</th> }</tr></thead>
          <tbody>
            @for (g of filas(); track g.titulo) {
              <tr><td>{{ g.titulo }}</td>@for (b of g.barras; track b.serie) { <td class="num">{{ b.texto }}</td> }</tr>
            }
          </tbody>
        </table>
      </div>
    </details>
  `,
})
export class GraficoColumnas {
  readonly series = input.required<string[]>();
  readonly grupos = input.required<GrupoColumnas[]>();
  readonly colores = input<string[]>(['var(--color-serie-1)', 'var(--color-serie-2)']);

  private readonly tope = computed(() => topeRedondo(Math.max(0, ...this.grupos().flatMap((g) => g.valores))));

  protected readonly marcas = computed(() => {
    const tope = this.tope();
    return [0, 0.25, 0.5, 0.75, 1].map((f) => ({ valor: f, altura: f * 100, texto: tope * f >= 1000 ? COMPACTO.format(tope * f) : `${Math.round(tope * f * 100) / 100}` }));
  });

  protected readonly filas = computed(() => this.grupos().map((g) => {
    const barras = this.series().map((serie, i) => ({
      serie,
      altura: (g.valores[i] / this.tope()) * 100,
      color: this.color(i),
      texto: USD.format(g.valores[i]),
    }));
    return { ...g, barras, resumen: `${g.titulo}: ${barras.map((b) => `${b.serie} ${b.texto}`).join(', ')}` };
  }));

  protected color(i: number): string {
    return this.colores()[i];
  }
}
