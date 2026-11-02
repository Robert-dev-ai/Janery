import { Component, inject } from '@angular/core';

import { InfoAppService } from '../../core/services/info-app';

/** Entrada al simulador: otra copia de Janery, con su propia base, para probar sin tocar los datos reales. */
@Component({
  selector: 'app-simulador',
  template: `
    <h1>Simulador</h1>
    <section class="seccion">
      <h2 class="seccion-titulo">Pruebe sin riesgo</h2>
      <p>El simulador es otra copia de Janery con su propia base de datos.</p>
      <ol class="list-decimal space-y-1 pl-5 text-sm">
        <li><strong>Iniciar simulación:</strong> copia todos los datos reales de ese momento: personas, cuentas, créditos, ahorros, socios y caja.</li>
        <li><strong>Pruebe lo que quiera:</strong> cree personas, pida y apruebe créditos, pague, abra ahorros o adelante la fecha para ver el interés, las letras y la mora.</li>
        <li><strong>Finalizar simulación:</strong> se descarta todo lo probado.</li>
      </ol>
      <p class="mt-3"><strong>Los datos reales nunca cambian:</strong> el simulador solo los lee, por una conexión que la base de datos no deja usar para escribir.</p>
      <p class="suave">Entra con su mismo correo y contraseña. Anote lo que se puede mejorar mientras prueba.</p>
    </section>
    @if (info.info()?.simuladorUrl; as url) {
      <a class="boton" [href]="url" target="_blank" rel="noopener">Abrir el simulador</a>
    } @else {
      <p class="alerta">El simulador aún no está publicado. Se activa con la variable JANERY_SIMULADOR_URL (ver docs/testing.md).</p>
    }
  `,
})
export class Simulador {
  protected readonly info = inject(InfoAppService);
}
