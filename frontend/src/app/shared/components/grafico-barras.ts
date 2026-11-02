import { Component, computed, input } from '@angular/core';

export interface BarraGrafico {
  etiqueta: string;
  valor: number;
  /** Texto del valor ya formateado, por ejemplo un importe. */
  texto: string;
}

/** Una sola serie: cada barra lleva su etiqueta y su valor escritos, así que no necesita leyenda ni depende del color. */
@Component({
  selector: 'app-grafico-barras',
  template: `
    @for (b of barras(); track b.etiqueta) {
      <div class="grafico-fila" [title]="b.etiqueta + ': ' + b.texto">
        <span>{{ b.etiqueta }}</span>
        <svg aria-hidden="true">
          <rect class="grafico-pista" width="100%" height="100%" rx="4" />
          @if (b.ancho > 0) { <rect class="grafico-barra" [attr.width]="b.ancho + '%'" height="100%" rx="4" /> }
        </svg>
        <span class="grafico-valor">{{ b.texto }}</span>
      </div>
    }
  `,
})
export class GraficoBarras {
  readonly datos = input.required<BarraGrafico[]>();

  /** Un valor negativo, como una pérdida, se escribe pero no dibuja barra. */
  protected readonly barras = computed(() => {
    const maximo = Math.max(0, ...this.datos().map((d) => d.valor));
    return this.datos().map((d) => ({ ...d, ancho: maximo > 0 ? (Math.max(d.valor, 0) / maximo) * 100 : 0 }));
  });
}
