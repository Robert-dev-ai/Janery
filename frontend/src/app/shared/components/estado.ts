import { Component, computed, input } from '@angular/core';

type Tono = 'exito' | 'informacion' | 'advertencia' | 'peligro' | 'neutro';

const ESTADOS: Record<string, { texto: string; tono: Tono }> = {
  ACTIVA: { texto: 'Activa', tono: 'exito' },
  ACTIVO: { texto: 'Activo', tono: 'exito' },
  PENDIENTE_PAGO: { texto: 'Pago pendiente', tono: 'advertencia' },
  PENDIENTE_CIERRE: { texto: 'Cierre pendiente', tono: 'advertencia' },
  SOLICITADO: { texto: 'Por aprobar', tono: 'advertencia' },
  RECHAZADA: { texto: 'Rechazada', tono: 'peligro' },
  RECHAZADO: { texto: 'Rechazado', tono: 'peligro' },
  CERRADA: { texto: 'Cerrada', tono: 'neutro' },
  CERRADO: { texto: 'Cerrado', tono: 'neutro' },
  FINALIZADO: { texto: 'Finalizado', tono: 'informacion' },
  PENDIENTE: { texto: 'Pendiente', tono: 'advertencia' },
  ENTREGADA: { texto: 'Entregada', tono: 'exito' },
  CONFIRMADA: { texto: 'Confirmada', tono: 'exito' },
  CANCELADA: { texto: 'Cancelada', tono: 'neutro' },
};

/** El estado se lee por el texto, no solo por el color. Un estado desconocido se muestra tal cual, en neutro. */
@Component({
  selector: 'app-estado',
  template: `<span class="estado" [class]="'estado-' + info().tono">{{ info().texto }}</span>`,
})
export class Estado {
  readonly valor = input.required<string>();
  protected readonly info = computed(() => ESTADOS[this.valor()] ?? { texto: this.valor(), tono: 'neutro' as Tono });
}
