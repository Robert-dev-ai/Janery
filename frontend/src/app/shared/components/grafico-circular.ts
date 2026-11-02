import { Component, computed, input, signal } from '@angular/core';

export interface PorcionGrafico {
  etiqueta: string;
  valor: number;
  /** Texto del valor ya formateado, por ejemplo un importe. */
  texto: string;
}

/** Máximo de porciones con color propio: las demás se suman en "Otros". */
const MAX_PORCIONES = 5;

/**
 * Partes de un total. Sirve para unas pocas categorías; cada una lleva su valor y porcentaje escritos en la leyenda,
 * así que no depende solo del color.
 */
@Component({
  selector: 'app-grafico-circular',
  template: `
    @if (total() > 0) {
      <div class="circular">
        <svg viewBox="0 0 120 120" role="img" [attr.aria-label]="descripcion()">
          @for (p of porciones(); track p.etiqueta) {
            <circle class="circular-porcion" cx="60" cy="60" r="48" pathLength="100" transform="rotate(-90 60 60)"
              [class.apagada]="activa() && activa() !== p.etiqueta" [style.stroke]="p.color"
              [attr.stroke-dasharray]="p.trazo + ' ' + (100 - p.trazo)" [attr.stroke-dashoffset]="-p.inicio"
              (mouseenter)="activa.set(p.etiqueta)" (mouseleave)="activa.set(null)" />
          }
          <text class="circular-valor" x="60" y="59">{{ centro().valor }}</text>
          <text class="circular-nota" x="60" y="72">{{ centro().nota }}</text>
        </svg>
        <ul class="leyenda">
          @for (p of porciones(); track p.etiqueta) {
            <li (mouseenter)="activa.set(p.etiqueta)" (mouseleave)="activa.set(null)">
              <i [style.background]="p.color"></i>
              <span>{{ p.etiqueta }}</span>
              <strong>{{ p.texto }}</strong>
              <small>{{ p.porcentaje }} %</small>
            </li>
          }
        </ul>
      </div>
    } @else {
      <p class="suave">Sin datos en este periodo.</p>
    }
  `,
})
export class GraficoCircular {
  readonly datos = input.required<PorcionGrafico[]>();
  /** Texto del total, ya formateado, para el centro del gráfico. */
  readonly textoTotal = input.required<string>();

  protected readonly activa = signal<string | null>(null);

  protected readonly total = computed(() => this.positivos().reduce((suma, d) => suma + d.valor, 0));

  private readonly positivos = computed(() => this.datos().filter((d) => d.valor > 0));

  protected readonly porciones = computed(() => {
    const total = this.total();
    const orden = [...this.positivos()].sort((a, b) => b.valor - a.valor);
    const resto = orden.slice(MAX_PORCIONES - 1);
    const visibles = orden.length > MAX_PORCIONES
      ? [...orden.slice(0, MAX_PORCIONES - 1), { etiqueta: 'Otros', valor: resto.reduce((s, d) => s + d.valor, 0), texto: USD.format(resto.reduce((s, d) => s + d.valor, 0)) }]
      : orden;
    let inicio = 0;
    return visibles.map((d, i) => {
      const parte = (d.valor / total) * 100;
      // El espacio entre porciones las separa; una porción diminuta conserva un trazo visible.
      const porcion = { ...d, inicio, trazo: visibles.length > 1 ? Math.max(parte - 0.8, 0.4) : 100, porcentaje: Math.round(parte), color: d.etiqueta === 'Otros' ? 'var(--color-serie-otros)' : `var(--color-serie-${i + 1})` };
      inicio += parte;
      return porcion;
    });
  });

  protected readonly centro = computed(() => {
    const p = this.porciones().find((x) => x.etiqueta === this.activa());
    return p ? { valor: p.texto, nota: `${p.etiqueta.slice(0, 18)} · ${p.porcentaje} %` } : { valor: this.textoTotal(), nota: 'Total' };
  });

  protected readonly descripcion = computed(() =>
    this.porciones().map((p) => `${p.etiqueta}: ${p.texto}, ${p.porcentaje} %`).join('; '));
}

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
