import { Component, inject } from '@angular/core';

import { InfoAppService } from '../../core/services/info-app';

/** Pie con el contacto de Janery y la versión del programa. */
@Component({
  selector: 'app-pie-contacto',
  host: { role: 'contentinfo' },
  template: `
    <p class="m-0"><strong>Caja Familiar Janery</strong> · Caja de ahorro y crédito</p>
    @if (info.info(); as i) {
      <p class="m-0">
        Contacto: <a [href]="'tel:+' + i.whatsapp">{{ i.telefono }}</a> · {{ i.direccion }}
      </p>
      <p class="m-0 text-xs">Versión {{ i.version }}@if (i.esSimulador) { · Simulador }</p>
    }
  `,
})
export class PieContacto {
  protected readonly info = inject(InfoAppService);
}
